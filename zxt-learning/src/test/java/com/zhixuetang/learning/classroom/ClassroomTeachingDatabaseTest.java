package com.zhixuetang.learning.classroom;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhixuetang.api.client.course.CourseClient;
import com.zhixuetang.api.client.learning.ClassroomExamClient;
import com.zhixuetang.api.client.user.UserClient;
import com.zhixuetang.api.dto.course.CourseFullInfoDTO;
import com.zhixuetang.api.dto.course.CourseSimpleInfoDTO;
import com.zhixuetang.api.dto.trade.OrderBasicDTO;
import com.zhixuetang.api.dto.user.UserDTO;
import com.zhixuetang.common.autoconfigure.reliability.OutboxStore;
import com.zhixuetang.common.exceptions.ConflictException;
import com.zhixuetang.common.exceptions.ForbiddenException;
import com.zhixuetang.common.utils.UserContext;
import com.zhixuetang.learning.service.impl.LearningEntitlementService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static com.zhixuetang.learning.classroom.ClassroomActivities.*;
import static com.zhixuetang.learning.classroom.ClassroomGrades.*;
import static com.zhixuetang.learning.classroom.Classrooms.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "ACCEPTANCE_DB_PASSWORD", matches = ".+")
class ClassroomTeachingDatabaseTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private Classrooms rooms;
    private ClassroomActivities activities;
    private ClassroomGrades grades;
    private LearningEntitlementService entitlements;
    private CourseSimpleInfoDTO metadata;
    private LocalDateTime now;
    private long course, admin, teacher, assistant, student, second;

    @BeforeEach
    void arrange() {
        var data = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"
                + System.getenv().getOrDefault("ACCEPTANCE_DB_PORT", "23316")
                + "/acceptance_learning?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true",
                "root", System.getenv("ACCEPTANCE_DB_PASSWORD"));
        jdbc = new JdbcTemplate(data);
        tx = new TransactionTemplate(new DataSourceTransactionManager(data));
        var courses = mock(CourseClient.class);
        var users = mock(UserClient.class);
        var exams = mock(ClassroomExamClient.class);
        entitlements = new LearningEntitlementService(jdbc);
        rooms = new Classrooms(jdbc, entitlements, courses, users);
        activities = new ClassroomActivities(rooms, jdbc, courses, exams, entitlements,
                new OutboxStore(jdbc, JsonMapper.builder().build()));
        grades = new ClassroomGrades(rooms, activities, exams, jdbc);
        course = IdWorker.getId();
        admin = IdWorker.getId();
        teacher = IdWorker.getId();
        assistant = IdWorker.getId();
        student = IdWorker.getId();
        second = IdWorker.getId();
        now = LocalDateTime.now().withNano(0);
        metadata = new CourseSimpleInfoDTO();
        metadata.setId(course);
        metadata.setStatus(2);
        metadata.setSectionNum(3);
        when(courses.getCourseInfoById(course, false, false)).thenReturn(new CourseFullInfoDTO());
        when(courses.getSimpleInfoList(List.of(course))).thenReturn(List.of(metadata));
        when(users.queryUserByIds(List.of(teacher))).thenReturn(List.of(user(teacher, 3)));
        when(users.queryUserByIds(List.of(assistant))).thenReturn(List.of(user(assistant, 3)));
        when(users.queryUserByIds(List.of(student))).thenReturn(List.of(user(student, 2)));
        when(users.queryUserByIds(List.of(second))).thenReturn(List.of(user(second, 2)));
    }

    @Test
    void reservesLastSeatOnceAndKeepsOrderProvenanceAcrossTransferAndRefund() throws Exception {
        var first = open("一期", 1);
        var next = open("二期", 1);
        var barrier = new CountDownLatch(2);
        var outcomes = new java.util.HashMap<Long, Long>();
        Callable<Boolean> bookStudent = booking(first.id(), student, outcomes, barrier);
        Callable<Boolean> bookSecond = booking(first.id(), second, outcomes, barrier);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(bookStudent);
            var b = pool.submit(bookSecond);
            assertNotEquals(a.get(), b.get());
        }
        assertEquals(1, outcomes.size());
        var booking = outcomes.entrySet().iterator().next();
        long owner = booking.getKey();
        long order = booking.getValue();
        long detail = IdWorker.getId();
        var paid = order(owner, order, detail);
        as(admin, 1, () -> {
            rooms.settleOrder(order, owner, List.of(course), false);
            entitlements.grant(paid, List.of(metadata));
            return null;
        });
        long member = rooms.currentMember(course, owner).id();
        assertEquals(MemberState.ACTIVE, rooms.currentMember(course, owner).state());
        as(admin, 1, () -> { rooms.move(first.id(), member, next.id(), "学习档期调整"); return null; });
        assertEquals(next.id(), rooms.currentMember(course, owner).classId());
        as(admin, 1, () -> {
            rooms.settleOrder(order, owner, List.of(course), true);
            entitlements.revoke(paid);
            rooms.settleOrder(order, owner, List.of(course), false);
            return null;
        });
        assertNull(rooms.currentMember(course, owner));
        assertEquals(1, rooms.get(next.id()).remaining());
        assertEquals(MemberState.TRANSFERRED, rooms.member(first.id(), member).state());
    }

    @Test
    void appliesIndividualDeadlineAndRestrictsAssistantToReview() {
        var first = open("一期", 2);
        var next = open("二期", 2);
        carryRights(student);
        var member = as(student, 2, () -> rooms.enroll(first.id(), student, false, null));
        var homework = form(first.id(), now.minusMinutes(10));
        var other = form(next.id(), now.plusMinutes(30));
        var request = new Submit(UUID.randomUUID(), 1, "作品", null);
        assertThrows(ConflictException.class, () -> as(student, 2, () -> activities.submit(first.id(), homework.id(), request)));
        assertThrows(ForbiddenException.class, () -> as(student, 2, () -> activities.submit(next.id(), other.id(), request)));
        as(teacher, 3, () -> {
            activities.extend(first.id(), homework.id(), new Extension(member.id(), now.plusMinutes(20), "补交"));
            return null;
        });
        var submission = as(student, 2, () -> activities.submit(first.id(), homework.id(), request));
        assertEquals(submission.id(), as(student, 2, () -> activities.submit(first.id(), homework.id(), request)).id());
        as(assistant, 3, () -> activities.review(first.id(), homework.id(), submission.id(),
                new Review(ReviewState.REVIEWED, 85, "已核对")));
        assertThrows(ForbiddenException.class, () -> as(assistant, 3, () -> {
            grades.publish(first.id(), homework.id(), new Publication(member.id(), null));
            return null;
        }));
        as(teacher, 3, () -> { grades.publish(first.id(), homework.id(), new Publication(member.id(), null)); return null; });
        assertEquals(85, as(student, 2, () -> grades.own(first.id(), member.id())).items().getFirst().score());
        as(admin, 1, () -> { rooms.assign(first.id(), assistant, null); return null; });
        assertThrows(ForbiddenException.class, () -> as(assistant, 3, () -> activities.teaching(first.id())));
    }

    @Test
    void archivesMissingWorkAndRecordsPostArchiveGradeCorrection() {
        var room = open("待结班", 1);
        carryRights(student);
        var member = as(student, 2, () -> rooms.enroll(room.id(), student, false, null));
        var assignment = form(room.id(), now.minusMinutes(20));
        as(admin, 1, () -> rooms.save(room.id(), new Setup(course, room.name(), room.registrationOpens(),
                now.minusMinutes(14), room.startsAt(), now.minusMinutes(12), room.capacity(), room.passPercent())));
        as(teacher, 3, () -> { grades.archive(room.id()); return null; });
        assertEquals(State.ARCHIVED, rooms.get(room.id()).state());
        assertEquals(0, as(student, 2, () -> grades.own(room.id(), member.id())).result().totalPercent().intValue());
        as(teacher, 3, () -> {
            grades.change(room.id(), assignment.id(), new Change(member.id(), 0, 82, "已核对", "纸质作业补录"));
            return null;
        });
        var report = as(student, 2, () -> grades.own(room.id(), member.id()));
        assertTrue(report.result().passed());
        assertEquals(82, report.result().totalPercent().intValue());
        assertEquals(0, jdbc.queryForObject("""
                SELECT old_score FROM teaching_class_grade_history WHERE member_id=? AND activity_id=?
                """, Integer.class, member.id(), assignment.id()));
    }

    private Callable<Boolean> booking(long id, long user, Map<Long, Long> outcomes, CountDownLatch barrier) {
        return () -> {
            barrier.countDown();
            barrier.await();
            long order = IdWorker.getId();
            try {
                as(user, 2, () -> { rooms.reserve(id, course, user, order); return null; });
                synchronized (outcomes) { outcomes.put(user, order); }
                return true;
            } catch (ConflictException e) {
                return false;
            }
        };
    }

    private Activity form(long id, LocalDateTime closes) {
        return as(admin, 1, () -> activities.publish(id, new Form(Kind.ASSIGNMENT, null, null,
                "教学任务", "提交作品说明", now.minusMinutes(40), closes, 100, null)));
    }

    private Classroom open(String name, int seatCount) {
        return as(admin, 1, () -> {
            var room = rooms.save(null, new Setup(course, name, now.minusHours(2), now.plusHours(1),
                    now.minusHours(1), now.plusHours(2), seatCount, 60));
            rooms.assign(room.id(), teacher, Duty.TEACHER);
            rooms.assign(room.id(), assistant, Duty.ASSISTANT);
            return rooms.changeState(room.id(), State.OPEN, null);
        });
    }

    private void carryRights(long learner) {
        as(learner, 2, () -> {
            entitlements.grant(order(learner, IdWorker.getId(), IdWorker.getId()), List.of(metadata));
            return null;
        });
    }

    private OrderBasicDTO order(long owner, long order, long detail) {
        return OrderBasicDTO.builder().userId(owner).orderId(order).courseIds(List.of(course))
                .detailIds(Map.of(course, detail)).validDurations(Map.of(course, 0)).finishTime(now).build();
    }

    private <T> T as(long user, long role, Supplier<T> action) {
        UserContext.setUser(user);
        UserContext.setRole(role);
        try { return tx.execute(status -> action.get()); }
        finally { UserContext.removeUser(); }
    }

    private static UserDTO user(long id, int kind) {
        var dto = new UserDTO();
        dto.setId(id);
        dto.setType(kind);
        dto.setName("合成验收账号");
        return dto;
    }
}
