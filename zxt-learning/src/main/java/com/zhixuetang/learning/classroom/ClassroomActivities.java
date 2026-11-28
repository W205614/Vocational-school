package com.zhixuetang.learning.classroom;

import com.zhixuetang.api.client.course.CourseClient;
import com.zhixuetang.api.client.learning.ClassroomClient.ExamAccess;
import com.zhixuetang.api.client.learning.ClassroomClient.ExamAccessRequest;
import com.zhixuetang.api.client.learning.ClassroomExamClient;
import com.zhixuetang.api.dto.message.ProductNotice;
import com.zhixuetang.common.autoconfigure.reliability.OutboxStore;
import com.zhixuetang.common.exceptions.BadRequestException;
import com.zhixuetang.common.exceptions.ConflictException;
import com.zhixuetang.common.exceptions.ForbiddenException;
import com.zhixuetang.common.utils.UserContext;
import com.zhixuetang.learning.service.impl.LearningEntitlementService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.zhixuetang.learning.classroom.Classrooms.*;

@Service
@RequiredArgsConstructor
public class ClassroomActivities {
    public enum Kind { LESSON, ASSIGNMENT, EXAM, ANNOUNCEMENT }
    public enum ReviewState { SUBMITTED, NEEDS_REVISION, REVIEWED }
    @Schema(name = "ClassroomActivityForm")
    public record Form(Kind kind, Long sectionId, Long paperId, String title, String instructions,
                       LocalDateTime scheduledAt, LocalDateTime closesAt, Integer weight, Integer maxAttempts) {}
    public record Activity(Long id, Long classId, Kind kind, Long sectionId, Long paperId, String title,
                           LocalDateTime scheduledAt, LocalDateTime closesAt, int weight, Integer maxAttempts,
                           int currentVersion, String instructions) {}
    @Schema(name = "ClassroomActivityVersion")
    public record Version(int version, String title, String instructions, String reason, LocalDateTime changedAt) {}
    @Schema(name = "ClassroomActivityCorrection")
    public record Correction(int expectedVersion, String title, String instructions, String reason) {}
    public record Extension(Long memberId, LocalDateTime closesAt, String reason) {}
    @Schema(name = "ClassroomAssignmentSubmit")
    public record Submit(UUID requestKey, int assignmentVersion, String description, String workUrl) {}
    @Schema(name = "ClassroomAssignmentReview")
    public record Review(ReviewState state, Integer score, String feedback) {}
    @Schema(name = "ClassroomAssignmentSubmission")
    public record Submission(Long id, Long memberId, Long userId, int version, int revision, String description,
                             String workUrl, ReviewState state, Integer score, String feedback, LocalDateTime submittedAt) {}
    public record DiscussionForm(Long parentId, Long sectionId, String title, String body) {}
    public record Discussion(Long id, Long parentId, Long sectionId, String title, String body,
                             boolean staffReply, LocalDateTime postedAt) {}
    @Schema(name = "ClassroomTeachingOverview")
    public record Pending(int assignments, int exams, int questions, int grades,
                          int assignmentSubmitted, int assignmentExpected, int examSubmitted, int examExpected) {}
    public record StudyView(Classroom classroom, Membership member, List<Activity> activities) {}
    public record Section(Long id, String name) {}

    private static final String ACTIVITIES = """
            SELECT a.*,v.instructions FROM teaching_class_activity a JOIN teaching_class_activity_version v
            ON v.activity_id=a.id AND v.version=a.current_version
            """;

    private final Classrooms rooms;
    private final JdbcTemplate jdbc;
    private final CourseClient courses;
    private final ClassroomExamClient exams;
    private final LearningEntitlementService entitlements;
    private final OutboxStore outbox;

    @Transactional(readOnly = true)
    public StudyView study(long id) {
        long user = student();
        var member = rooms.studentMemberForRead(id, user);
        return new StudyView(rooms.get(id), member, jdbc.query(ACTIVITIES + """
                WHERE a.class_id=? AND (a.kind<>'ANNOUNCEMENT' OR a.scheduled_at<=NOW(3))
                ORDER BY a.scheduled_at,a.id
                """, ClassroomActivities::activity, id));
    }

    @Transactional(readOnly = true)
    public List<Activity> teaching(long id) {
        rooms.staffAccess(id, false);
        return all(id);
    }

    public List<ClassroomExamClient.Paper> papers(long id) {
        rooms.staffAccess(id, true);
        return exams.papers(rooms.get(id).courseId());
    }

    public List<Section> sections(long id) {
        rooms.staffAccess(id, true);
        var course = courses.getCourseInfoById(rooms.get(id).courseId(), true, false);
        if (course == null || course.getChapters() == null) return List.of();
        return course.getChapters().stream().filter(chapter -> chapter.getSections() != null)
                .flatMap(chapter -> chapter.getSections().stream())
                .map(section -> new Section(section.getId(), section.getName())).toList();
    }

    @Transactional
    public Activity publish(long id, Form form) {
        Classroom room = rooms.locked(id);
        rooms.staffAccess(id, true);
        editable(room);
        return insert(room, form);
    }

    @Transactional
    public List<Activity> copy(long id, long source, Map<Long, Long> replacementPapers) {
        UserContext.requireAdmin();
        if (source == id) throw new BadRequestException("请选择往期班级");
        Classroom destination = rooms.locked(id);
        if (destination.state() != State.DRAFT || !all(id).isEmpty())
            throw new ConflictException("请选择尚未安排教学的草稿班级");
        Classroom original = rooms.get(source);
        if (!destination.courseId().equals(original.courseId()))
            throw new BadRequestException("请选择同一课程的班级");
        var offset = java.time.Duration.between(original.startsAt(), destination.startsAt());
        for (Activity a : all(source)) {
            Long paper = a.kind() == Kind.EXAM && replacementPapers != null
                    ? replacementPapers.getOrDefault(a.id(), a.paperId()) : a.paperId();
            insert(destination, new Form(a.kind(), a.sectionId(), paper, a.title(), a.instructions(),
                    a.scheduledAt().plus(offset), a.closesAt() == null ? null : a.closesAt().plus(offset),
                    a.weight(), a.maxAttempts()));
        }
        rooms.audit(id, UserContext.requireUser(), null, "SCHEDULE_COPIED", Long.toString(source));
        return all(id);
    }

    private Activity insert(Classroom room, Form form) {
        String title = required(form.title(), 120, "标题");
        String instructions = required(form.instructions(), 8000, "教学说明");
        if (form.kind() == null || form.scheduledAt() == null || form.scheduledAt().isBefore(room.startsAt())
                || !form.scheduledAt().isBefore(room.endsAt()))
            throw new BadRequestException("教学安排须在班级开结课时间内");
        boolean graded = form.kind() == Kind.ASSIGNMENT || form.kind() == Kind.EXAM;
        int weight = form.weight() == null ? 0 : form.weight();
        if (graded && (form.closesAt() == null || !form.closesAt().isAfter(form.scheduledAt())
                || form.closesAt().isAfter(room.endsAt()) || weight < 1 || weight > 100)
                || !graded && (form.closesAt() != null || weight != 0))
            throw new BadRequestException("考核时间或权重无效");
        Long section = form.sectionId();
        if (form.kind() == Kind.EXAM) {
            if (form.paperId() == null || form.maxAttempts() == null
                    || form.maxAttempts() < 1 || form.maxAttempts() > 10)
                throw new BadRequestException("请选择班级试卷并设置允许作答次数");
            var paper = exams.papers(room.courseId()).stream().filter(p -> p.id().equals(form.paperId())).findFirst()
                    .orElseThrow(() -> new BadRequestException("试卷不属于该课程的班级试卷"));
            if (paperRevealed(paper.id())) throw new ConflictException("试卷解析已开放，请选择新的试卷版本");
            section = paper.sectionId();
        } else if (form.paperId() != null || form.maxAttempts() != null) {
            throw new BadRequestException("只有考试可以选择试卷和作答次数");
        } else if (form.kind() == Kind.LESSON && section == null) {
            throw new BadRequestException("请选择课程小节");
        }
        if (section != null && form.kind() != Kind.EXAM) checkSection(room.courseId(), section);
        Integer used = jdbc.queryForObject("""
                SELECT COALESCE(SUM(weight),0) FROM teaching_class_activity WHERE class_id=?
                """, Integer.class, room.id());
        if (used + weight > 100) throw new ConflictException("考核总权重不能超过 100%");
        long activity = rooms.insert("""
                INSERT INTO teaching_class_activity(class_id,kind,section_id,paper_id,title,scheduled_at,
                closes_at,weight,max_attempts) VALUES(?,?,?,?,?,?,?,?,?)
                """, room.id(), form.kind().name(), section, form.paperId(), title, form.scheduledAt(),
                form.closesAt(), weight, form.maxAttempts());
        jdbc.update("""
                INSERT INTO teaching_class_activity_version(activity_id,version,title,instructions) VALUES(?,1,?,?)
                """, activity, title, instructions);
        return get(room.id(), activity);
    }

    @Transactional
    public Activity correct(long id, long activityId, Correction form) {
        Classroom room = rooms.locked(id);
        rooms.staffAccess(id, true);
        editable(room);
        Activity current = get(id, activityId);
        if (current.currentVersion() != form.expectedVersion()) throw new ConflictException("教学安排已被更新，请刷新");
        String title = required(form.title(), 120, "标题");
        String text = required(form.instructions(), 8000, "教学说明");
        String reason = required(form.reason(), 500, "更正原因");
        int version = current.currentVersion() + 1;
        jdbc.update("UPDATE teaching_class_activity SET title=?,current_version=? WHERE id=?", title, version, activityId);
        jdbc.update("""
                INSERT INTO teaching_class_activity_version(activity_id,version,title,instructions,reason)
                VALUES(?,?,?,?,?)
                """, activityId, version, title, text, reason);
        if (room.state() != State.DRAFT)
            for (Long user : jdbc.query("""
                    SELECT user_id FROM teaching_class_member WHERE class_id=? AND state='ACTIVE'
                    """, (rs, row) -> rs.getLong(1), id))
                notice("class:activity:" + activityId + ":" + version + ":" + user, room, user,
                        "班级教学安排已更正", title + "：" + reason);
        return get(id, activityId);
    }

    @Transactional(readOnly = true)
    public List<Version> versions(long id, long activityId, boolean teaching) {
        if (teaching) rooms.staffAccess(id, false);
        else rooms.studentMemberForRead(id, student());
        Activity activity = get(id, activityId);
        if (!teaching && activity.kind() == Kind.ANNOUNCEMENT && activity.scheduledAt().isAfter(LocalDateTime.now()))
            throw new ForbiddenException("公告尚未发布");
        return jdbc.query("""
                SELECT * FROM teaching_class_activity_version WHERE activity_id=? ORDER BY version DESC
                """, (rs, row) -> new Version(rs.getInt("version"), rs.getString("title"),
                rs.getString("instructions"), rs.getString("reason"), rs.getObject("changed_at", LocalDateTime.class)), activityId);
    }

    @Transactional
    public void extend(long id, long activityId, Extension form) {
        Classroom room = rooms.locked(id);
        rooms.staffAccess(id, true);
        editable(room);
        Activity activity = get(id, activityId);
        if (form.memberId() == null || form.closesAt() == null || activity.closesAt() == null
                || !form.closesAt().isAfter(activity.closesAt()) || form.closesAt().isAfter(room.endsAt())
                || !form.closesAt().isAfter(LocalDateTime.now()))
            throw new BadRequestException("个人截止时间须晚于原截止时间且在结班前");
        var member = rooms.member(id, form.memberId());
        if (member.state() != MemberState.ACTIVE) throw new ConflictException("学生当前无有效班籍");
        String reason = required(form.reason(), 500, "延期原因");
        if (jdbc.queryForObject("""
                SELECT COUNT(*) FROM teaching_class_grade WHERE activity_id=? AND member_id=?
                """, Integer.class, activityId, form.memberId()) != 0)
            throw new ConflictException("该考核成绩已发布");
        if (activity.kind() == Kind.EXAM && paperRevealed(activity.paperId()))
            throw new ConflictException("试卷解析已开放，请安排另一份试卷");
        jdbc.update("""
                INSERT INTO teaching_class_extension(activity_id,member_id,closes_at) VALUES(?,?,?)
                ON DUPLICATE KEY UPDATE closes_at=GREATEST(closes_at,?)
                """, activityId, form.memberId(), form.closesAt(), form.closesAt());
        rooms.audit(id, UserContext.requireUser(), member.userId(), "DEADLINE_EXTENDED", reason);
    }

    @Transactional
    public Submission submit(long id, long activityId, Submit form) {
        Classroom room = rooms.locked(id);
        editable(room);
        long user = student();
        var member = rooms.studentMemberForWrite(id, user);
        Activity activity = assignment(id, activityId);
        if (form.requestKey() == null) throw new BadRequestException("缺少提交标识");
        String text = required(form.description(), 5000, "作品说明");
        String url = workUrl(form.workUrl());
        var replay = jdbc.query("""
                SELECT * FROM teaching_class_submission WHERE activity_id=? AND member_id=? AND request_key=?
                AND cleaned_at IS NULL
                """, ClassroomActivities::submission, activityId, member.id(), form.requestKey().toString());
        if (!replay.isEmpty()) {
            var previous = replay.getFirst();
            if (previous.version() != form.assignmentVersion() || !previous.description().equals(text)
                    || !java.util.Objects.equals(previous.workUrl(), url))
                throw new ConflictException("提交标识已用于另一份作品");
            return previous;
        }
        requireWindow(activity, deadline(activity, member.id()));
        if (form.assignmentVersion() != activity.currentVersion())
            throw new ConflictException("作业要求已更正，请刷新");
        var previous = submissions(activityId, member.id());
        if (!previous.isEmpty() && previous.getFirst().state() != ReviewState.NEEDS_REVISION)
            throw new ConflictException("已有待批改或已确认的作品");
        int revision = previous.isEmpty() ? 1 : previous.getFirst().revision() + 1;
        long submission = rooms.insert("""
                INSERT INTO teaching_class_submission(activity_id,member_id,user_id,request_key,assignment_version,
                revision,description,work_url) VALUES(?,?,?,?,?,?,?,?)
                """, activityId, member.id(), user, form.requestKey().toString(), form.assignmentVersion(), revision, text, url);
        return findSubmission(activityId, submission);
    }

    @Transactional(readOnly = true)
    public List<Submission> ownSubmissions(long id, long activityId, long memberId) {
        long user = student();
        if (rooms.member(id, memberId).userId() != user) throw new ForbiddenException("只能查看本人的提交");
        assignment(id, activityId);
        return submissions(activityId, memberId).stream().map(s -> new Submission(s.id(), s.memberId(), s.userId(),
                s.version(), s.revision(), s.description(), s.workUrl(), s.state(), null,
                s.state() == ReviewState.NEEDS_REVISION ? s.feedback() : null, s.submittedAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<Submission> teachingSubmissions(long id, long activityId) {
        rooms.staffAccess(id, false);
        assignment(id, activityId);
        return jdbc.query("""
                SELECT * FROM teaching_class_submission WHERE activity_id=? AND cleaned_at IS NULL
                ORDER BY member_id,revision DESC
                """, ClassroomActivities::submission, activityId);
    }

    @Transactional
    public Submission review(long id, long activityId, long submissionId, Review form) {
        Classroom room = rooms.locked(id);
        rooms.staffAccess(id, false);
        editable(room);
        assignment(id, activityId);
        var item = findSubmission(activityId, submissionId);
        var latest = submissions(activityId, item.memberId()).getFirst();
        if (!item.id().equals(latest.id()) || item.state() == ReviewState.REVIEWED
                || item.state() == ReviewState.NEEDS_REVISION && form.state() != ReviewState.REVIEWED)
            throw new ConflictException("作品已处理");
        if (form.state() == null || form.state() == ReviewState.SUBMITTED
                || form.state() == ReviewState.REVIEWED && !score(form.score())
                || form.state() == ReviewState.NEEDS_REVISION && form.score() != null)
            throw new BadRequestException("初评状态或分数无效");
        String feedback = required(form.feedback(), 5000, "评语");
        jdbc.update("""
                UPDATE teaching_class_submission SET state=?,score=?,feedback=?,reviewer_id=? WHERE id=?
                """, form.state().name(), form.score(), feedback, UserContext.requireUser(), submissionId);
        if (form.state() == ReviewState.NEEDS_REVISION)
            notice("class:submission:" + submissionId, room, item.userId(), "班级作业需要修改", feedback);
        return findSubmission(activityId, submissionId);
    }

    @Transactional(readOnly = true)
    public List<Discussion> discussion(long id, boolean teaching, Long before) {
        if (teaching) rooms.staffAccess(id, false);
        else rooms.studentMemberForRead(id, student());
        return jdbc.query("""
                SELECT * FROM teaching_class_discussion WHERE class_id=? AND cleaned_at IS NULL
                AND (? IS NULL OR id<?) ORDER BY id DESC LIMIT 100
                """, ClassroomActivities::discussion, id, before, before);
    }

    @Transactional
    public Discussion discuss(long id, boolean teaching, DiscussionForm form) {
        Classroom room = rooms.locked(id);
        editable(room);
        long user = teaching ? UserContext.requireUser() : student();
        if (teaching) rooms.staffAccess(id, false);
        else rooms.studentMemberForWrite(id, user);
        if (room.state() == State.DRAFT) throw new ConflictException("班级尚未发布");
        String text = required(form.body(), 8000, "内容");
        String title = null;
        if (form.parentId() != null) {
            if (form.sectionId() != null || form.title() != null || jdbc.queryForObject("""
                    SELECT COUNT(*) FROM teaching_class_discussion
                    WHERE class_id=? AND id=? AND parent_id IS NULL AND cleaned_at IS NULL
                    """, Integer.class, id, form.parentId()) == 0)
                throw new BadRequestException("班级问题不存在");
        } else {
            title = required(form.title(), 120, "问题标题");
            if (form.sectionId() != null) checkSection(room.courseId(), form.sectionId());
        }
        long post = rooms.insert("""
                INSERT INTO teaching_class_discussion(class_id,parent_id,section_id,user_id,title,body,staff_reply)
                VALUES(?,?,?,?,?,?,?)
                """, id, form.parentId(), form.sectionId(), user, title, text, teaching);
        if (teaching && form.parentId() != null) {
            long owner = jdbc.queryForObject("SELECT user_id FROM teaching_class_discussion WHERE id=?",
                    Long.class, form.parentId());
            if (owner != user) notice("class:discussion:" + post, room, owner, "班级答疑有新回复", "打开班级查看回复。");
        }
        return jdbc.query("SELECT * FROM teaching_class_discussion WHERE id=?",
                ClassroomActivities::discussion, post).getFirst();
    }

    @Transactional(readOnly = true)
    public Pending pending(long id) {
        rooms.staffAccess(id, false);
        int assignments = jdbc.queryForObject("""
                SELECT COUNT(*) FROM teaching_class_submission s JOIN teaching_class_activity a ON a.id=s.activity_id
                WHERE a.class_id=? AND s.state='SUBMITTED' AND s.cleaned_at IS NULL
                """, Integer.class, id);
        int questions = jdbc.queryForObject("""
                SELECT COUNT(*) FROM teaching_class_discussion d WHERE d.class_id=? AND d.parent_id IS NULL
                AND d.cleaned_at IS NULL AND NOT EXISTS(SELECT 1 FROM teaching_class_discussion r
                WHERE r.class_id=d.class_id AND r.parent_id=d.id AND r.staff_reply AND r.cleaned_at IS NULL)
                """, Integer.class, id);
        int ready = jdbc.queryForObject("""
                SELECT COUNT(*) FROM teaching_class_submission s JOIN teaching_class_activity a ON a.id=s.activity_id
                WHERE a.class_id=? AND s.state='REVIEWED' AND s.cleaned_at IS NULL
                AND NOT EXISTS(SELECT 1 FROM teaching_class_grade g
                WHERE g.activity_id=s.activity_id AND g.member_id=s.member_id)
                """, Integer.class, id);
        int examCount = 0;
        int examSubmitted = 0;
        int examExpected = 0;
        int assignmentExpected = 0;
        Set<Long> active = Set.copyOf(jdbc.query("""
                SELECT id FROM teaching_class_member WHERE class_id=? AND state IN ('ACTIVE','COMPLETED')
                """, (rs, row) -> rs.getLong(1), id));
        for (Activity activity : all(id)) {
            if (activity.kind() == Kind.ASSIGNMENT) assignmentExpected += active.size();
            if (activity.kind() != Kind.EXAM) continue;
            examExpected += active.size();
            var results = exams.results(activity.id());
            examCount += (int) results.stream().filter(ClassroomExamClient.ExamResult::pending).count();
            examSubmitted += (int) results.stream().filter(r -> active.contains(r.memberId())
                    && (r.pending() || r.bestPercent() != null)).count();
            var published = Set.copyOf(jdbc.query("SELECT member_id FROM teaching_class_grade WHERE activity_id=?",
                    (rs, row) -> rs.getLong(1), activity.id()));
            ready += (int) results.stream().filter(r -> !r.pending() && r.bestPercent() != null
                    && !published.contains(r.memberId())).count();
        }
        int assignmentSubmitted = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT s.member_id,s.activity_id)
                FROM teaching_class_submission s JOIN teaching_class_activity a ON a.id=s.activity_id
                JOIN teaching_class_member m ON m.id=s.member_id WHERE a.class_id=?
                AND m.state IN ('ACTIVE','COMPLETED') AND s.cleaned_at IS NULL
                """, Integer.class, id);
        return new Pending(assignments, examCount, questions, ready, assignmentSubmitted,
                assignmentExpected, examSubmitted, examExpected);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ExamAccess examAccess(ExamAccessRequest request) {
        var rows = jdbc.query(ACTIVITIES + " WHERE a.id=? AND a.kind='EXAM'", ClassroomActivities::activity,
                request.activityId());
        if (rows.isEmpty()) throw new BadRequestException("班级考试不存在");
        Activity activity = rows.getFirst();
        Classroom room = rooms.get(activity.classId());
        Long memberId = request.memberId();
        Long lessonId = null;
        LocalDateTime closes = activity.closesAt();
        if ("GRADE".equals(request.action()) || "TEACHING".equals(request.action())) {
            if (request.roleId() == null || !Set.of(1L, 3L).contains(request.roleId()))
                throw new ForbiddenException("需要教学人员账号");
            if (request.roleId() == 3L) rooms.staffAccess(room.id(), request.userId(), false);
            if ("GRADE".equals(request.action())) editable(room);
        } else if ("OPEN".equals(request.action()) || "VIEW".equals(request.action())) {
            if (!Long.valueOf(2).equals(request.roleId())) throw new ForbiddenException("需要学生账号");
            if ("OPEN".equals(request.action())) {
                editable(room);
                var member = rooms.activeStudentMember(room.id(), request.userId());
                if (memberId != null && !member.id().equals(memberId))
                    throw new ForbiddenException("该答卷属于往期班籍");
                memberId = member.id();
                lessonId = entitlements.require(request.userId(), room.courseId());
                closes = deadline(activity, memberId);
                requireWindow(activity, closes);
            } else if (memberId == null || rooms.member(room.id(), memberId).userId() != request.userId()) {
                throw new ForbiddenException("只能查看本人的答卷");
            }
        } else {
            throw new BadRequestException("考试操作无效");
        }
        boolean showSolutions = "VIEW".equals(request.action()) && releasePaper(activity.paperId());
        return new ExamAccess(room.id(), activity.paperId(), room.courseId(), memberId, lessonId,
                activity.maxAttempts(), closes, showSolutions);
    }

    Activity get(long id, long activity) {
        return jdbc.query(ACTIVITIES + " WHERE a.class_id=? AND a.id=?", ClassroomActivities::activity, id, activity)
                .stream().findFirst().orElseThrow(() -> new BadRequestException("班级教学安排不存在"));
    }

    List<Activity> all(long id) {
        return jdbc.query(ACTIVITIES + " WHERE a.class_id=? ORDER BY a.scheduled_at,a.id", ClassroomActivities::activity, id);
    }

    List<Submission> submissions(long activity, long member) {
        return jdbc.query("""
                SELECT * FROM teaching_class_submission WHERE activity_id=? AND member_id=? AND cleaned_at IS NULL
                ORDER BY revision DESC
                """, ClassroomActivities::submission, activity, member);
    }

    LocalDateTime deadline(Activity activity, long member) {
        return jdbc.query("""
                SELECT closes_at FROM teaching_class_extension WHERE activity_id=? AND member_id=?
                """, (rs, row) -> rs.getObject(1, LocalDateTime.class), activity.id(), member)
                .stream().findFirst().orElse(activity.closesAt());
    }

    void notice(String operation, Classroom room, long user, String title, String text) {
        outbox.enqueue(operation, "learning.topic", "learning.notice.product",
                new ProductNotice(operation, user, title, text.substring(0, Math.min(240, text.length())),
                        "CLASSROOM", room.id(), room.courseId(), null));
    }

    boolean releasePaper(long paper) {
        if (paperRevealed(paper)) return true;
        boolean closed = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT NOT EXISTS(SELECT 1 FROM teaching_class_activity a JOIN teaching_class c ON c.id=a.class_id
                LEFT JOIN teaching_class_extension x ON x.activity_id=a.id
                WHERE a.paper_id=? AND c.state<>'CANCELED' AND GREATEST(a.closes_at,COALESCE(x.closes_at,a.closes_at))>NOW(3))
                """, Boolean.class, paper));
        if (closed) jdbc.update("UPDATE teaching_class_paper_guard SET revealed_at=NOW(3) WHERE paper_id=?", paper);
        return closed;
    }

    private boolean paperRevealed(long paper) {
        jdbc.update("""
                INSERT INTO teaching_class_paper_guard(paper_id) VALUES(?) ON DUPLICATE KEY UPDATE paper_id=paper_id
                """, paper);
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT revealed_at IS NOT NULL FROM teaching_class_paper_guard WHERE paper_id=? FOR UPDATE
                """, Boolean.class, paper));
    }

    private Activity assignment(long id, long activityId) {
        Activity activity = get(id, activityId);
        if (activity.kind() != Kind.ASSIGNMENT) throw new BadRequestException("班级作业不存在");
        return activity;
    }

    private Submission findSubmission(long activity, long id) {
        return jdbc.query("""
                SELECT * FROM teaching_class_submission WHERE activity_id=? AND id=? AND cleaned_at IS NULL
                """, ClassroomActivities::submission, activity, id).stream().findFirst()
                .orElseThrow(() -> new BadRequestException("作业提交不存在"));
    }

    private void checkSection(long course, long sectionId) {
        var section = courses.sectionInfo(sectionId);
        if (section == null || section.getCourseId() == null || section.getCourseId() != course)
            throw new BadRequestException("小节不属于该课程");
    }

    private static void requireWindow(Activity activity, LocalDateTime deadline) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.scheduledAt()) || !now.isBefore(deadline))
            throw new ConflictException("当前不在允许提交的时间内");
    }

    static boolean score(Integer score) {
        return score != null && score >= 0 && score <= 100;
    }

    private static String workUrl(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI uri = URI.create(url.strip());
            if (url.length() <= 2048 && Set.of("http", "https").contains(uri.getScheme())
                    && uri.getHost() != null && uri.getUserInfo() == null) return uri.toString();
        } catch (IllegalArgumentException ignored) {
            throw new BadRequestException("作品链接无效");
        }
        throw new BadRequestException("作品链接须使用 HTTP 或 HTTPS");
    }

    private static Activity activity(ResultSet rs, int row) throws SQLException {
        return new Activity(rs.getLong("id"), rs.getLong("class_id"), Kind.valueOf(rs.getString("kind")),
                rs.getObject("section_id", Long.class), rs.getObject("paper_id", Long.class),
                rs.getString("title"), rs.getObject("scheduled_at", LocalDateTime.class),
                rs.getObject("closes_at", LocalDateTime.class), rs.getInt("weight"),
                rs.getObject("max_attempts", Integer.class), rs.getInt("current_version"), rs.getString("instructions"));
    }

    private static Submission submission(ResultSet rs, int row) throws SQLException {
        return new Submission(rs.getLong("id"), rs.getLong("member_id"), rs.getLong("user_id"),
                rs.getInt("assignment_version"), rs.getInt("revision"), rs.getString("description"),
                rs.getString("work_url"), ReviewState.valueOf(rs.getString("state")),
                rs.getObject("score", Integer.class), rs.getString("feedback"),
                rs.getObject("submitted_at", LocalDateTime.class));
    }

    private static Discussion discussion(ResultSet rs, int row) throws SQLException {
        return new Discussion(rs.getLong("id"), rs.getObject("parent_id", Long.class), rs.getObject("section_id", Long.class),
                rs.getString("title"), rs.getString("body"), rs.getBoolean("staff_reply"),
                rs.getObject("posted_at", LocalDateTime.class));
    }
}
