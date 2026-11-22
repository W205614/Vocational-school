package com.zhixuetang.exam.reliability;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhixuetang.common.autoconfigure.mvc.advice.CommonExceptionAdvice;
import com.zhixuetang.common.utils.UserContext;
import com.zhixuetang.exam.controller.InternalExamController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL and MVC: counts include every paper and never expose answers or another actor's attempts. */
@EnabledIfEnvironmentVariable(named = "ACCEPTANCE_DB_PASSWORD", matches = ".+")
class LearningExamReminderTest {
    private JdbcTemplate jdbc;
    private MockMvc mvc;
    private long user, other, course;

    @BeforeEach
    void prepare() {
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                "jdbc:mysql://127.0.0.1:"
                                        + System.getenv()
                                                .getOrDefault("ACCEPTANCE_DB_PORT", "23316")
                                        + "/acceptance_exam?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true",
                                "root",
                                System.getenv("ACCEPTANCE_DB_PASSWORD")));
        assertEquals("acceptance_exam", jdbc.queryForObject("SELECT DATABASE()", String.class));
        user = IdWorker.getId();
        other = IdWorker.getId();
        course = IdWorker.getId();
        UserContext.setUser(user);
        mvc =
                MockMvcBuilders.standaloneSetup(new InternalExamController(jdbc))
                        .setControllerAdvice(new CommonExceptionAdvice())
                        .build();
        assertNotNull(System.getenv("ZXT_INTERNAL_TOKEN"));
        assertTrue(System.getenv("ZXT_INTERNAL_TOKEN").length() >= 32);
    }

    @AfterEach
    void cleanup() {
        UserContext.removeUser();
    }

    private long paper(long section, int version) {
        long id = IdWorker.getId();
        jdbc.update(
                "INSERT INTO exam_paper(id,course_id,section_id,version,total_score,pass_percent,section_count) VALUES(?,?,?,?,10,60,1)",
                id,
                course,
                section,
                version);
        return id;
    }

    private long attempt(long paper, long actor, String status, boolean passed) {
        long id = IdWorker.getId();
        jdbc.update(
                "INSERT INTO exam_attempt(id,paper_id,user_id,lesson_id,status,passed) VALUES(?,?,?,?,?,?)",
                id,
                paper,
                actor,
                course,
                status,
                passed);
        return id;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            String body) {
        return post("/internal/v1/exams/learning-overview")
                .header("X-Internal-Token", System.getenv("ZXT_INTERNAL_TOKEN"))
                .contentType("application/json")
                .content(body);
    }

    @Test
    void latestPaperCountsAreOwnerScopedAndSeparateWaitingFromActionable() throws Exception {
        long section = IdWorker.getId(), old = paper(section, 1), latest = paper(section, 2);
        attempt(old, user, "FINISHED", true);
        attempt(latest, other, "FINISHED", true);
        long waiting = paper(IdWorker.getId(), 1);
        attempt(waiting, user, "WAIT_GRADING", false);
        attempt(waiting, user, "FINISHED", false);
        long passed = paper(IdWorker.getId(), 1);
        attempt(passed, user, "FINISHED", true);
        attempt(passed, user, "FINISHED", false);
        long active = paper(IdWorker.getId(), 1);
        attempt(active, user, "IN_PROGRESS", false);
        long failed = paper(IdWorker.getId(), 1);
        attempt(failed, user, "FINISHED", false);
        attempt(failed, user, "FINISHED", false);
        paper(IdWorker.getId(), 1);
        mvc.perform(request("[" + course + "]").param("userId", Long.toString(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingExams").value(4))
                .andExpect(jsonPath("$.waitingForGradingExams").value(1))
                .andExpect(jsonPath("$.answer").doesNotExist());
    }

    @Test
    void nextAttemptUsesDraftActivityAndNeverUsesAnotherStudentsDraft() throws Exception {
        long first = paper(IdWorker.getId(), 1), second = paper(IdWorker.getId(), 1);
        long idle = attempt(first, user, "IN_PROGRESS", false);
        long active = attempt(second, user, "IN_PROGRESS", false);
        long foreign = attempt(first, other, "IN_PROGRESS", false);
        jdbc.update("UPDATE exam_attempt SET created_at='2026-11-01 08:00:00' WHERE id IN (?,?,?)",
                idle, active, foreign);
        jdbc.update("INSERT INTO exam_draft(attempt_id,answers,updated_at) VALUES(?,'{}','2026-11-04 08:00:00'),(?,'{}','2026-11-05 08:00:00')",
                active, foreign);
        mvc.perform(request("[" + course + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeAttempt.attemptId").value(org.hamcrest.Matchers.is(active), Long.class))
                .andExpect(jsonPath("$.pendingReview").doesNotExist());
    }

    @Test
    void publishedSectionsReturnDistinctIdsAndRequireInternalService() throws Exception {
        long section = IdWorker.getId();
        paper(section, 1);
        paper(section, 2);
        mvc.perform(get("/internal/v1/exams/published-sections").param("courseId", Long.toString(course)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/v1/exams/published-sections")
                        .header("X-Internal-Token", System.getenv("ZXT_INTERNAL_TOKEN"))
                        .param("courseId", Long.toString(course)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value(org.hamcrest.Matchers.is(section), Long.class));
    }

    @Test
    void moreThanOneHundredPapersAreCountedWithoutPagination() throws Exception {
        for (int i = 0; i < 121; i++) paper(IdWorker.getId(), 1);
        mvc.perform(request("[" + course + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingExams").value(121))
                .andExpect(jsonPath("$.waitingForGradingExams").value(0));
    }

    @Test
    void internalTokenAndActorAreRequiredAndRpcLimitIsNotRelaxed() throws Exception {
        mvc.perform(
                        post("/internal/v1/exams/learning-overview")
                                .contentType("application/json")
                                .content("[" + course + "]"))
                .andExpect(status().isForbidden());
        UserContext.removeUser();
        mvc.perform(request("[" + course + "]")).andExpect(status().isUnauthorized());
        UserContext.setUser(user);
        for (String invalid :
                List.of(
                        "[]",
                        "[0]",
                        "[null]",
                        "[1,1]",
                        java.util.stream.LongStream.rangeClosed(1, 101)
                                .boxed()
                                .toList()
                                .toString()))
            mvc.perform(request(invalid)).andExpect(status().isBadRequest());
    }
}
