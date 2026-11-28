package com.zhixuetang.learning.classroom;

import com.zhixuetang.api.client.course.CourseClient;
import com.zhixuetang.api.client.user.UserClient;
import com.zhixuetang.api.constants.CourseStatus;
import com.zhixuetang.common.exceptions.BadRequestException;
import com.zhixuetang.common.exceptions.ConflictException;
import com.zhixuetang.common.exceptions.ForbiddenException;
import com.zhixuetang.common.utils.UserContext;
import com.zhixuetang.learning.service.impl.LearningEntitlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class Classrooms {
    public enum State { DRAFT, OPEN, CLOSED, ARCHIVED, CANCELED }
    public enum Duty { ADMIN, TEACHER, ASSISTANT }
    public enum MemberState { HELD, ACTIVE, WITHDRAWN, TRANSFERRED, REFUNDED, RELEASED, COMPLETED }

    public record Setup(long courseId, String name, LocalDateTime registrationOpens,
                        LocalDateTime registrationCloses, LocalDateTime startsAt, LocalDateTime endsAt,
                        int capacity, int passPercent) {}
    public record Classroom(Long id, Long courseId, String name, State state, LocalDateTime registrationOpens,
                            LocalDateTime registrationCloses, LocalDateTime startsAt, LocalDateTime endsAt,
                            int capacity, int passPercent, int remaining) {}
    public record Membership(Long id, Long classId, Long courseId, Long userId, Long orderId,
                             MemberState state, LocalDateTime joinedAt) {}
    public record ClassMembership(Classroom classroom, Membership member) {}
    public record Staff(Long userId, String name, Duty duty) {}
    public record RosterRow(Long memberId, Long userId, String name, MemberState state,
                            int learnedSections, Integer courseSections) {}

    private static final String CLASS = """
            SELECT c.*,c.capacity-(SELECT COUNT(*) FROM teaching_class_member m
            WHERE m.class_id=c.id AND m.state IN ('HELD','ACTIVE')) AS remaining FROM teaching_class c
            """;

    private final JdbcTemplate jdbc;
    private final LearningEntitlementService entitlements;
    private final CourseClient courses;
    private final UserClient users;

    @Transactional
    public Classroom save(Long id, Setup form) {
        UserContext.requireAdmin();
        String name = required(form.name(), 120, "班级名称");
        if (form.courseId() <= 0 || form.capacity() < 1 || form.capacity() > 1000
                || form.passPercent() < 0 || form.passPercent() > 100
                || form.registrationOpens() == null || form.registrationCloses() == null
                || form.startsAt() == null || form.endsAt() == null
                || !form.registrationOpens().isBefore(form.registrationCloses())
                || !form.startsAt().isBefore(form.endsAt())
                || form.registrationCloses().isAfter(form.endsAt()))
            throw new BadRequestException("班级时间、容量或及格线无效");
        var course = courses.getCourseInfoById(form.courseId(), false, false);
        if (course == null) throw new BadRequestException("课程不存在");
        if (id == null) {
            id = insert("""
                    INSERT INTO teaching_class(course_id,name,registration_opens,registration_closes,starts_at,
                    ends_at,capacity,pass_percent) VALUES(?,?,?,?,?,?,?,?)
                    """, form.courseId(), name, form.registrationOpens(), form.registrationCloses(),
                    form.startsAt(), form.endsAt(), form.capacity(), form.passPercent());
            audit(id, UserContext.requireUser(), null, "CLASS_CREATED", null);
        } else {
            Classroom current = locked(id);
            editable(current);
            if (form.courseId() != current.courseId().longValue())
                throw new BadRequestException("班级课程已确定");
            if (form.capacity() < current.capacity() - current.remaining())
                throw new ConflictException("容量低于当前占用名额");
            if (current.state() != State.DRAFT && current.passPercent() != form.passPercent())
                throw new ConflictException("已发布班级的及格线已锁定");
            if (!form.startsAt().equals(current.startsAt()) || !form.endsAt().equals(current.endsAt())) {
                Integer outside = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM teaching_class_activity WHERE class_id=?
                        AND (scheduled_at<? OR scheduled_at>=? OR closes_at>?)
                        """, Integer.class, id, form.startsAt(), form.endsAt(), form.endsAt());
                if (outside != 0) throw new ConflictException("已有教学安排超出新的开结课时间");
            }
            jdbc.update("""
                    UPDATE teaching_class SET name=?,registration_opens=?,registration_closes=?,
                    starts_at=?,ends_at=?,capacity=?,pass_percent=? WHERE id=?
                    """, name, form.registrationOpens(), form.registrationCloses(), form.startsAt(),
                    form.endsAt(), form.capacity(), form.passPercent(), id);
            audit(id, UserContext.requireUser(), null, "CLASS_UPDATED", null);
        }
        return get(id);
    }

    @Transactional
    public Classroom changeState(long id, State target, String reason) {
        UserContext.requireAdmin();
        Classroom current = locked(id);
        if (current.state() == target) return current;
        if (target == State.OPEN && current.state() == State.DRAFT) {
            var course = courses.getSimpleInfoList(List.of(current.courseId()));
            if (course.stream().noneMatch(item -> CourseStatus.SHELF.equalsValue(item.getStatus())))
                throw new ConflictException("请先上架课程");
            if (jdbc.queryForObject("""
                    SELECT COUNT(*) FROM teaching_class_staff WHERE class_id=? AND duty='TEACHER'
                    """, Integer.class, id) == 0)
                throw new ConflictException("请先指定负责教师");
        } else if (target == State.CLOSED && current.state() == State.OPEN) {
            // Held orders can still settle after registration closes.
        } else if (target == State.CANCELED && (current.state() == State.DRAFT || current.state() == State.CLOSED
                || current.state() == State.OPEN)) {
            reason = required(reason, 500, "取消原因");
            if (current.capacity() != current.remaining())
                throw new ConflictException("请先处理班级占座、退班及退款");
        } else {
            throw new ConflictException("当前班级状态无法执行此操作");
        }
        jdbc.update("UPDATE teaching_class SET state=? WHERE id=?", target.name(), id);
        audit(id, UserContext.requireUser(), null, "CLASS_" + target.name(), reason);
        return get(id);
    }

    @Transactional
    public void assign(long id, long user, Duty duty) {
        UserContext.requireAdmin();
        Classroom current = locked(id);
        if (current.state() == State.CANCELED || duty == Duty.ADMIN)
            throw new BadRequestException("人员安排无效");
        if (duty != null && users.queryUserByIds(List.of(user)).stream()
                .noneMatch(item -> Integer.valueOf(3).equals(item.getType())))
            throw new BadRequestException("请选择有效的教师账号");
        if (duty != Duty.TEACHER && current.state() != State.DRAFT
                && jdbc.queryForObject("""
                        SELECT COUNT(*) FROM teaching_class_staff WHERE class_id=? AND user_id=? AND duty='TEACHER'
                        """, Integer.class, id, user) != 0
                && jdbc.queryForObject("""
                        SELECT COUNT(*) FROM teaching_class_staff WHERE class_id=? AND user_id<>? AND duty='TEACHER'
                        """, Integer.class, id, user) == 0)
            throw new ConflictException("请先安排接手教师");
        if (duty == null) {
            jdbc.update("DELETE FROM teaching_class_staff WHERE class_id=? AND user_id=?", id, user);
        } else {
            jdbc.update("""
                    INSERT INTO teaching_class_staff(class_id,user_id,duty) VALUES(?,?,?)
                    ON DUPLICATE KEY UPDATE duty=?
                    """, id, user, duty.name(), duty.name());
        }
        audit(id, UserContext.requireUser(), null, "STAFF_CHANGED", user + " " + (duty == null ? "REMOVED" : duty));
    }

    @Transactional(readOnly = true)
    public List<Staff> staff(long id) {
        staffAccess(id, false);
        var rows = jdbc.query("""
                SELECT user_id,duty FROM teaching_class_staff WHERE class_id=? ORDER BY duty,user_id
                """, (rs, row) -> new Staff(rs.getLong(1), null, Duty.valueOf(rs.getString(2))), id);
        Map<Long, String> names = names(rows.stream().map(Staff::userId).toList());
        return rows.stream().map(s -> new Staff(s.userId(), names.get(s.userId()), s.duty())).toList();
    }

    public List<Classroom> administered() {
        UserContext.requireAdmin();
        return jdbc.query(CLASS + " ORDER BY c.id DESC LIMIT 250", Classrooms::classroom);
    }

    public List<Classroom> assigned() {
        long user = UserContext.requireUser();
        if (Long.valueOf(1).equals(UserContext.getRole())) return administered();
        if (!Long.valueOf(3).equals(UserContext.getRole())) throw new ForbiddenException("需要教学人员账号");
        return jdbc.query(CLASS + """
                JOIN teaching_class_staff s ON s.class_id=c.id WHERE s.user_id=?
                AND c.state NOT IN ('DRAFT','CANCELED') ORDER BY c.id DESC LIMIT 250
                """, Classrooms::classroom, user);
    }

    public List<Classroom> available(long course) {
        if (course <= 0) throw new BadRequestException("课程编号无效");
        var open = jdbc.query(CLASS + """
                WHERE c.course_id=? AND c.state='OPEN' AND c.registration_opens<=NOW(3)
                AND c.registration_closes>NOW(3) ORDER BY c.starts_at,c.id
                """, Classrooms::classroom, course);
        if (open.isEmpty()) return open;
        return courses.getSimpleInfoList(List.of(course)).stream()
                .anyMatch(item -> CourseStatus.SHELF.equalsValue(item.getStatus())) ? open : List.of();
    }

    public List<ClassMembership> mine() {
        long user = student();
        return jdbc.query("SELECT * FROM teaching_class_member WHERE user_id=? ORDER BY id DESC",
                Classrooms::membership, user).stream()
                .map(m -> new ClassMembership(get(m.classId()), m)).toList();
    }

    @Transactional
    public Membership enroll(long id, long user, boolean administrative, String reason) {
        Classroom room = locked(id);
        var current = currentMember(room.courseId(), user);
        if (current != null && current.classId() == id && current.state() == MemberState.ACTIVE)
            return current;
        if (administrative) {
            UserContext.requireAdmin();
            if (users.queryUserByIds(List.of(user)).stream().noneMatch(u -> Integer.valueOf(2).equals(u.getType())))
                throw new BadRequestException("请选择有效的学生账号");
            required(reason, 500, "补录原因");
        }
        enrollable(room, administrative);
        entitlements.require(user, room.courseId());
        long member = addMember(room, user, null, null, MemberState.ACTIVE);
        audit(id, UserContext.requireUser(), user, administrative ? "MEMBER_ADDED" : "MEMBER_ENROLLED", reason);
        return member(id, member);
    }

    @Transactional
    public void reserve(long id, long course, long user, long order) {
        Classroom room = locked(id);
        if (room.courseId() != course) throw new BadRequestException("班级不属于订单课程");
        var existing = jdbc.query("SELECT * FROM teaching_class_member WHERE order_id=?", Classrooms::membership, order);
        if (!existing.isEmpty()) {
            Membership booked = existing.getFirst();
            if (booked.classId() != id || booked.userId() != user || booked.state() != MemberState.HELD)
                throw new ConflictException("订单与班级占座记录冲突");
            return;
        }
        enrollable(room, false);
        if (entitlements.findAvailableReadOnly(user, course) != null)
            throw new ConflictException("已有课程权益，请直接报名班级");
        addMember(room, user, order, order, MemberState.HELD);
        audit(id, user, user, "SEAT_HELD", Long.toString(order));
    }

    @Transactional
    public void settleOrder(long order, long user, List<Long> courses, boolean refund) {
        var booked = jdbc.query("""
                SELECT * FROM teaching_class_member WHERE order_id=? AND user_id=?
                """, Classrooms::membership, order, user);
        if (booked.isEmpty() || !courses.contains(booked.getFirst().courseId())) return;
        var reservation = booked.getFirst();
        locked(reservation.classId());
        if (refund) {
            var affected = jdbc.query("""
                    SELECT * FROM teaching_class_member WHERE source_order_id=? AND state IN ('HELD','ACTIVE')
                    FOR UPDATE
                    """, Classrooms::membership, order);
            for (var m : affected) {
                changeMember(m.id(), MemberState.REFUNDED);
                audit(m.classId(), null, user, "MEMBER_REFUNDED", Long.toString(order));
            }
        } else if (jdbc.update("""
                UPDATE teaching_class_member SET state='ACTIVE',joined_at=NOW(3)
                WHERE id=? AND state='HELD'
                """, reservation.id()) != 0) {
            audit(reservation.classId(), null, user, "MEMBER_PAID", Long.toString(order));
        }
    }

    @Transactional
    public void releaseOrder(long order) {
        var rows = jdbc.query("SELECT * FROM teaching_class_member WHERE order_id=?", Classrooms::membership, order);
        if (rows.isEmpty()) return;
        Membership reservation = rows.getFirst();
        locked(reservation.classId());
        if (jdbc.update("""
                UPDATE teaching_class_member SET state='RELEASED',ended_at=NOW(3) WHERE id=? AND state='HELD'
                """, reservation.id()) != 0)
            audit(reservation.classId(), null, reservation.userId(), "SEAT_RELEASED", Long.toString(order));
    }

    @Transactional
    public void move(long id, long memberId, Long target, String reason) {
        UserContext.requireAdmin();
        reason = required(reason, 500, "退班或转班原因");
        if (target != null && target == id) throw new BadRequestException("请选择其他班级");
        if (target != null && target < id) locked(target);
        Classroom source = locked(id);
        Classroom destination = target == null ? null : locked(target);
        editable(source);
        Membership current = member(id, memberId);
        if (current.state() != MemberState.ACTIVE) throw new ConflictException("仅已入班的学生可退班或转班");
        if (target == null) {
            changeMember(memberId, MemberState.WITHDRAWN);
            audit(id, UserContext.requireUser(), current.userId(), "MEMBER_WITHDRAWN", reason);
            return;
        }
        if (!source.courseId().equals(destination.courseId()))
            throw new BadRequestException("只能转入同一课程的另一期");
        enrollable(destination, true);
        entitlements.require(current.userId(), source.courseId());
        Long order = jdbc.queryForObject("SELECT source_order_id FROM teaching_class_member WHERE id=?",
                Long.class, memberId);
        changeMember(memberId, MemberState.TRANSFERRED);
        addMember(destination, current.userId(), null, order, MemberState.ACTIVE);
        audit(id, UserContext.requireUser(), current.userId(), "MEMBER_TRANSFERRED_OUT", reason);
        audit(target, UserContext.requireUser(), current.userId(), "MEMBER_TRANSFERRED_IN", reason);
    }

    @Transactional(readOnly = true)
    public List<RosterRow> roster(long id) {
        staffAccess(id, false);
        Classroom room = get(id);
        var info = courses.getSimpleInfoList(List.of(room.courseId()));
        Integer sections = info.isEmpty() ? null : info.getFirst().getSectionNum();
        var rows = jdbc.query("""
                SELECT m.id,m.user_id,m.state,COALESCE(l.learned_sections,0) AS sections
                FROM teaching_class_member m LEFT JOIN learning_lesson l
                ON l.user_id=m.user_id AND l.course_id=m.course_id AND m.state='ACTIVE'
                WHERE m.class_id=? AND m.state IN ('ACTIVE','COMPLETED','WITHDRAWN','TRANSFERRED','REFUNDED')
                ORDER BY m.id
                """, (rs, row) -> new RosterRow(rs.getLong("id"), rs.getLong("user_id"), null,
                MemberState.valueOf(rs.getString("state")), rs.getInt("sections"), sections), id);
        Map<Long, String> names = names(rows.stream().map(RosterRow::userId).distinct().toList());
        return rows.stream().map(row -> new RosterRow(row.memberId(), row.userId(), names.get(row.userId()),
                row.state(), row.learnedSections(), row.courseSections())).toList();
    }

    Classroom get(long id) {
        return jdbc.query(CLASS + " WHERE c.id=?", Classrooms::classroom, id).stream().findFirst()
                .orElseThrow(() -> new BadRequestException("班级不存在"));
    }

    Classroom locked(long id) {
        if (jdbc.query("SELECT id FROM teaching_class WHERE id=? FOR UPDATE", (rs, row) -> rs.getLong(1), id).isEmpty())
            throw new BadRequestException("班级不存在");
        return get(id);
    }

    Duty staffAccess(long id, boolean teacherOnly) {
        long actor = UserContext.requireUser();
        if (Long.valueOf(1).equals(UserContext.getRole())) return Duty.ADMIN;
        if (!Long.valueOf(3).equals(UserContext.getRole())) throw new ForbiddenException("需要教学人员账号");
        return staffAccess(id, actor, teacherOnly);
    }

    Duty staffAccess(long id, long actor, boolean teacherOnly) {
        var duties = jdbc.query("""
                SELECT s.duty FROM teaching_class_staff s JOIN teaching_class c ON c.id=s.class_id
                WHERE s.class_id=? AND s.user_id=? AND c.state NOT IN ('DRAFT','CANCELED')
                """, (rs, row) -> Duty.valueOf(rs.getString(1)), id, actor);
        if (duties.isEmpty() || teacherOnly && duties.getFirst() != Duty.TEACHER)
            throw new ForbiddenException("缺少该班级的教学权限");
        return duties.getFirst();
    }

    Membership member(long id, long memberId) {
        return jdbc.query("SELECT * FROM teaching_class_member WHERE class_id=? AND id=?",
                        Classrooms::membership, id, memberId).stream().findFirst()
                .orElseThrow(() -> new BadRequestException("班级成员不存在"));
    }

    Membership studentMemberForRead(long id, long user) {
        Membership member = studentMember(id, user, true);
        if (member.state() == MemberState.ACTIVE && entitlements.findAvailableReadOnly(user, member.courseId()) == null)
            throw new ForbiddenException("无有效课程权益");
        return member;
    }

    Membership studentMemberForWrite(long id, long user) {
        Membership member = activeStudentMember(id, user);
        entitlements.require(user, member.courseId());
        return member;
    }

    Membership activeStudentMember(long id, long user) {
        return studentMember(id, user, false);
    }

    private Membership studentMember(long id, long user, boolean includeCompleted) {
        var rows = jdbc.query("""
                SELECT * FROM teaching_class_member WHERE class_id=? AND user_id=?
                AND (state='ACTIVE' OR (? AND state='COMPLETED')) ORDER BY id DESC LIMIT 1
                """, Classrooms::membership, id, user, includeCompleted);
        if (rows.isEmpty()) throw new ForbiddenException("你没有该班级的有效班籍");
        return rows.getFirst();
    }

    Membership currentMember(long course, long user) {
        return jdbc.query("SELECT * FROM teaching_class_member WHERE active_course_id=? AND user_id=?",
                Classrooms::membership, course, user).stream().findFirst().orElse(null);
    }

    void audit(long id, Long actor, Long student, String event, String detail) {
        jdbc.update("""
                INSERT INTO teaching_class_event(class_id,actor_id,student_id,event_type,detail) VALUES(?,?,?,?,?)
                """, id, actor, student, event, detail);
    }

    Map<Long, String> names(List<Long> ids) {
        var names = new java.util.HashMap<Long, String>();
        for (int from = 0; from < ids.size(); from += 100) {
            for (var user : users.queryUserByIds(ids.subList(from, Math.min(from + 100, ids.size()))))
                names.put(user.getId(), user.getName());
        }
        return names;
    }

    long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    private long addMember(Classroom room, long user, Long order, Long sourceOrder, MemberState state) {
        try {
            return insert("""
                    INSERT INTO teaching_class_member(class_id,course_id,user_id,order_id,source_order_id,state)
                    VALUES(?,?,?,?,?,?)
                    """, room.id(), room.courseId(), user, order, sourceOrder, state.name());
        } catch (DuplicateKeyException e) {
            throw new ConflictException("该课程已有尚未结束的班籍或订单");
        }
    }

    private void changeMember(long id, MemberState state) {
        jdbc.update("UPDATE teaching_class_member SET state=?,ended_at=NOW(3) WHERE id=?", state.name(), id);
    }

    private void enrollable(Classroom room, boolean administrative) {
        LocalDateTime now = LocalDateTime.now();
        if (room.state() != State.OPEN && !(administrative && room.state() == State.CLOSED)
                || !now.isBefore(room.endsAt())
                || !administrative && (now.isBefore(room.registrationOpens()) || !now.isBefore(room.registrationCloses())))
            throw new ConflictException("班级当前未开放报名");
        if (room.remaining() <= 0) throw new ConflictException("班级名额已满");
    }

    static void editable(Classroom room) {
        if (room.state() == State.ARCHIVED || room.state() == State.CANCELED)
            throw new ConflictException("班级已结束");
    }

    static long student() {
        long user = UserContext.requireUser();
        if (!Long.valueOf(2).equals(UserContext.getRole())) throw new ForbiddenException("需要学生账号");
        return user;
    }

    static String required(String value, int max, String field) {
        if (value == null || value.isBlank() || value.strip().length() > max)
            throw new BadRequestException(field + "不能为空且最多 " + max + " 字");
        return value.strip();
    }

    private static Classroom classroom(ResultSet rs, int row) throws SQLException {
        return new Classroom(rs.getLong("id"), rs.getLong("course_id"), rs.getString("name"),
                State.valueOf(rs.getString("state")), rs.getObject("registration_opens", LocalDateTime.class),
                rs.getObject("registration_closes", LocalDateTime.class), rs.getObject("starts_at", LocalDateTime.class),
                rs.getObject("ends_at", LocalDateTime.class), rs.getInt("capacity"), rs.getInt("pass_percent"),
                rs.getInt("remaining"));
    }

    private static Membership membership(ResultSet rs, int row) throws SQLException {
        return new Membership(rs.getLong("id"), rs.getLong("class_id"), rs.getLong("course_id"), rs.getLong("user_id"),
                rs.getObject("order_id", Long.class), MemberState.valueOf(rs.getString("state")),
                rs.getObject("joined_at", LocalDateTime.class));
    }
}
