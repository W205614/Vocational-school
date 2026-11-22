package com.zhixuetang.learning.reliability;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhixuetang.api.client.course.CourseClient;
import com.zhixuetang.api.client.user.UserClient;
import com.zhixuetang.api.dto.course.CourseFullInfoDTO;
import com.zhixuetang.common.autoconfigure.reliability.OutboxStore;
import com.zhixuetang.common.exceptions.BadRequestException;
import com.zhixuetang.common.exceptions.ConflictException;
import com.zhixuetang.common.exceptions.ForbiddenException;
import com.zhixuetang.learning.service.impl.LearningEntitlementService;
import com.zhixuetang.learning.service.impl.LearningHubOperationHandler;
import com.zhixuetang.learning.service.impl.PracticeAssignmentService;
import com.zhixuetang.learning.service.impl.PracticeAssignmentService.AssignmentVersion;
import com.zhixuetang.learning.service.impl.PracticeAssignmentService.AssignmentView;
import com.zhixuetang.learning.service.impl.PracticeAssignmentService.Configuration;
import com.zhixuetang.learning.service.impl.PracticeAssignmentService.Correction;
import com.zhixuetang.learning.service.impl.PracticeAssignmentService.Feedback;
import com.zhixuetang.learning.service.impl.PracticeAssignmentService.Status;
import com.zhixuetang.learning.service.impl.PracticeAssignmentService.StudentAssignment;
import com.zhixuetang.learning.service.impl.PracticeAssignmentService.Submission;
import com.zhixuetang.learning.service.impl.PracticeAssignmentService.Submit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "ACCEPTANCE_DB_PASSWORD", matches = ".+")
class PracticeAssignmentDatabaseTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private PracticeAssignmentService practice;
    private long student;
    private long course;
    private long admin;

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource(
                "jdbc:mysql://127.0.0.1:" + System.getenv().getOrDefault("ACCEPTANCE_DB_PORT", "23316")
                        + "/acceptance_learning?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true",
                "root", System.getenv("ACCEPTANCE_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        student = IdWorker.getId();
        course = IdWorker.getId();
        admin = IdWorker.getId();
        var courses = mock(CourseClient.class);
        when(courses.getCourseInfoById(course, false, false)).thenReturn(new CourseFullInfoDTO());
        practice = new PracticeAssignmentService(jdbc, courses, mock(UserClient.class),
                new LearningEntitlementService(jdbc), mock(LearningHubOperationHandler.class),
                new OutboxStore(jdbc, JsonMapper.builder().findAndAddModules().build()));
        addLesson(student);
        addEntitlement(student, true, null);
        transactions.executeWithoutResult(status ->
                practice.configure(course, admin, new Configuration("原题目", "原任务说明", true)));
    }

    @Test
    void correctionsPreserveWorkAndRequireTheReviewedVersionForResubmission() {
        var firstForm = new Submit("第一份作品", null, UUID.randomUUID(), 1);
        Submission first = submit(firstForm);
        assertEquals(1, first.assignmentVersion());
        assertThrows(BadRequestException.class, () -> correct(new Correction("新题目", "新任务说明", " ", 1)));
        var correction = new Correction("新题目", "新任务说明", "补充验收项", 1);
        var updated = correct(correction);
        assertEquals(List.of(2, 1), updated.versions().stream().map(AssignmentVersion::version).toList());
        assertEquals("补充验收项", updated.versions().getFirst().changeReason());
        assertEquals("原任务说明", updated.versions().getLast().instructions());
        assertEquals(updated, correct(correction));
        assertEquals(first, submit(firstForm));
        assertThrows(ConflictException.class, () -> submit(new Submit(
                firstForm.description(), null, firstForm.submissionKey(), 2)));
        transactions.executeWithoutResult(status -> practice.review(
                course, first.id(), admin, 1L, new Feedback(Status.NEEDS_REVISION, "补充说明")));
        assertThrows(ConflictException.class, () -> submit(new Submit("新作品", null, UUID.randomUUID(), 1)));
        assertThrows(ConflictException.class, () -> submit(new Submit("旧客户端作品", null, UUID.randomUUID(), null)));
        assertEquals(2, submit(new Submit("新作品", null, UUID.randomUUID(), 2)).assignmentVersion());
        assertEquals(List.of(2, 1), practice.student(course, student).submissions().stream()
                .map(Submission::assignmentVersion).toList());
    }

    @Test
    void expiredStudentsOnlySeeVersionsAttachedToTheirOwnWorks() {
        submit(new Submit("第一份作品", null, UUID.randomUUID(), 1));
        correct(new Correction("新题目", "新任务说明", "补充验收项", 1));
        assertEquals(List.of(new StudentAssignment(course, "新题目")), practice.assignments(student));
        jdbc.update("UPDATE learning_entitlement SET active=0 WHERE user_id=? AND course_id=?", student, course);
        assertEquals(List.of(new StudentAssignment(course, "原题目")), practice.assignments(student));
        var retained = practice.student(course, student);
        assertTrue(retained.historyOnly());
        assertFalse(retained.canSubmit());
        assertEquals("原任务说明", retained.assignment().instructions());
        assertEquals(List.of(1), retained.versions().stream().map(AssignmentVersion::version).toList());
        assertThrows(ForbiddenException.class, () -> submit(new Submit("新作品", null, UUID.randomUUID(), 2)));
        assertThrows(ForbiddenException.class, () -> practice.student(course, IdWorker.getId()));
    }

    @Test
    void concurrentCorrectionsPersistOnlyOneNewVersion() throws Exception {
        var first = new Correction("新题目一", "新任务说明一", "更正理由一", 1);
        var second = new Correction("新题目二", "新任务说明二", "更正理由二", 1);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> concurrentCorrection(first, ready, start));
            var b = pool.submit(() -> concurrentCorrection(second, ready, start));
            boolean bothReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(bothReady);
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        }
        var stored = practice.configuration(course);
        assertEquals(List.of(2, 1), stored.versions().stream().map(AssignmentVersion::version).toList());
        var winner = stored.assignment().title().equals(first.title()) ? first : second;
        var stale = winner == first ? second : first;
        assertEquals(stored, correct(winner));
        assertThrows(ConflictException.class, () -> correct(stale));
    }

    @Test
    void correctionQueuesOneNoticePerAffectedLearnerInThePublicationTransaction() {
        addEntitlement(student, true, null);
        long pending = IdWorker.getId();
        long passed = IdWorker.getId();
        long expired = IdWorker.getId();
        long revoked = IdWorker.getId();
        for (long learner : List.of(pending, passed, expired, revoked)) addLesson(learner);
        addEntitlement(pending, true, null);
        addEntitlement(passed, true, null);
        addEntitlement(expired, true, LocalDateTime.now().minusDays(1));
        addEntitlement(revoked, false, null);
        addEntitlement(IdWorker.getId(), true, null); // An entitlement without a lesson cannot open the assignment.
        for (long learner : List.of(pending, passed)) {
            Submission submitted = transactions.execute(status -> practice.submit(course, learner,
                    new Submit("作品", null, UUID.randomUUID(), 1)));
            if (learner == passed) transactions.executeWithoutResult(status -> practice.review(
                    course, submitted.id(), admin, 1L, new Feedback(Status.PASSED, "通过")));
        }

        var correction = new Correction("新版任务", "增加自评", "补充要求", 1);
        String prefix = "practice-correction:" + course + ":2:";
        transactions.executeWithoutResult(status -> {
            practice.correct(course, admin, correction);
            status.setRollbackOnly();
        });
        assertTrue(correctionNoticeKeys(prefix).isEmpty());
        assertEquals(1, practice.configuration(course).assignment().version());

        correct(correction);
        correct(correction);
        assertEquals(List.of(prefix + student, prefix + pending).stream().sorted().toList(),
                correctionNoticeKeys(prefix));
        var payload = JsonMapper.builder().build().readTree(jdbc.queryForObject(
                "SELECT payload FROM reliability_outbox WHERE business_key=?", String.class, prefix + student))
                .path("payload");
        assertEquals("PRACTICE_CORRECTION", payload.path("targetType").asText());
        assertEquals(course, payload.path("courseId").asLong());
        assertEquals(2, payload.path("targetId").asLong());
    }

    private List<String> correctionNoticeKeys(String prefix) {
        return jdbc.queryForList("SELECT business_key FROM reliability_outbox WHERE business_key LIKE ? ORDER BY business_key",
                String.class, prefix + "%");
    }

    private void addLesson(long learner) {
        jdbc.update("INSERT INTO learning_lesson(id,user_id,course_id,status,learned_sections) VALUES(?,?,?,0,0)",
                IdWorker.getId(), learner, course);
    }

    private void addEntitlement(long learner, boolean active, LocalDateTime expiresAt) {
        jdbc.update("INSERT INTO learning_entitlement(order_detail_id,order_id,user_id,course_id,active,expires_at) VALUES(?,?,?,?,?,?)",
                IdWorker.getId(), IdWorker.getId(), learner, course, active, expiresAt);
    }

    private boolean concurrentCorrection(Correction correction, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            correct(correction);
            return true;
        } catch (ConflictException changed) {
            return false;
        }
    }

    private AssignmentView correct(Correction correction) {
        return transactions.execute(status -> practice.correct(course, admin, correction));
    }

    private Submission submit(Submit form) {
        return transactions.execute(status -> practice.submit(course, student, form));
    }
}
