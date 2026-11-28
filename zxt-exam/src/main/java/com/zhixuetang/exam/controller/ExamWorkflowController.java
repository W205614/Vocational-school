package com.zhixuetang.exam.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zhixuetang.api.client.course.CourseClient;
import com.zhixuetang.api.client.learning.LearningClient;
import com.zhixuetang.api.dto.course.ExamSectionPublicationDTO;
import com.zhixuetang.common.autoconfigure.reliability.OperationStore;
import com.zhixuetang.common.domain.R;
import com.zhixuetang.common.exceptions.*;
import com.zhixuetang.common.utils.UserContext;
import com.zhixuetang.exam.service.impl.ExamWorkflowService;
import com.zhixuetang.exam.service.impl.ExamWorkflowService.*;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2")
public class ExamWorkflowController {
    private final ExamWorkflowService service;
    private final OperationStore operations;
    private final CourseClient courses;
    private final LearningClient learning;
    private final JdbcTemplate jdbc;
    private final com.zhixuetang.api.client.user.UserClient users;

    public record PublishRequest(
            long courseId,
            long sectionId,
            List<Long> questionIds,
            Integer passPercent,
            List<Long> graders,
            Map<Long, Explanation> explanations,
            @JsonInclude(JsonInclude.Include.NON_NULL) Boolean classroomOnly) {}

    @PostMapping("/admin/exam-papers")
    public ResponseEntity<OperationStore.View> publish(
            @RequestBody PublishRequest form, @RequestHeader("Idempotency-Key") String key) {
        UserContext.requireAdmin();
        if (form.courseId() <= 0 || form.sectionId() <= 0)
            throw new BadRequestException("课程和考试小节标识无效");
        var section = courses.examSectionForPublication(form.courseId(), form.sectionId());
        if (section == null) throw new BadRequestException("考试小节不属于该课程");
        boolean draft = section.draftQuestionIds() != null;
        if (form.questionIds() == null
                || form.questionIds().isEmpty()
                || form.questionIds().size() > 100
                || form.questionIds().stream().anyMatch(id -> id == null || id <= 0))
            throw new BadRequestException("试卷题目参数无效");
        if (draft && !new HashSet<>(section.draftQuestionIds()).containsAll(form.questionIds()))
            throw new BadRequestException("题目未关联到该草稿的考试小节");
        if (form.graders() != null && !form.graders().isEmpty()) {
            if (form.graders().size() > 100
                    || form.graders().stream().anyMatch(id -> id == null || id <= 0))
                throw new BadRequestException("评分教师参数无效");
            var assigned = users.queryUserByIds(form.graders());
            if (assigned.size() != new HashSet<>(form.graders()).size()
                    || assigned.stream().anyMatch(u -> !Integer.valueOf(3).equals(u.getType())))
                throw new BadRequestException("评分人必须是有效教师");
        }
        if (form.explanations() != null && !form.explanations().isEmpty()) {
            if (form.explanations().size() > 100
                    || !new HashSet<>(form.questionIds()).containsAll(form.explanations().keySet()))
                throw new BadRequestException("讲解须关联到试卷中的题目");
            var verified = new HashMap<Long, ExamSectionPublicationDTO>();
            for (var explanation : form.explanations().values()) {
                if (explanation == null || explanation.sectionId() <= 0)
                    throw new BadRequestException("讲解视频小节无效");
                var lecture = verified.computeIfAbsent(
                        explanation.sectionId(),
                        id -> courses.lectureSectionForPublication(form.courseId(), id));
                if (lecture == null) throw new BadRequestException("讲解视频小节不属于该课程");
                Integer moment = explanation.moment();
                if (moment != null
                        && (moment < 0
                                || lecture.mediaDuration() == null
                                || moment >= lecture.mediaDuration()))
                    throw new BadRequestException("讲解时间须在视频时长内");
            }
        }
        return ResponseEntity.accepted()
                .body(
                        operations.submit(
                                UserContext.requireUser(),
                                "EXAM_PUBLISH",
                                key,
                                form,
                                new Publish(
                                        form.courseId(),
                                        form.sectionId(),
                                        form.questionIds(),
                                        form.passPercent(),
                                        form.graders(),
                                        section.totalSections(),
                                        draft,
                                        form.explanations(),
                                        form.classroomOnly())));
    }

    @GetMapping("/exam-papers")
    public List<Map<String, Object>> papers(@RequestParam long courseId) {
        UserContext.requireUser();
        if (learning.isLessonValid(courseId) == null) throw new BadRequestException("无课程学习权限");
        return jdbc.queryForList(
                "SELECT id,course_id,section_id,version,total_score,pass_percent FROM exam_paper WHERE course_id=? AND classroom_only=FALSE ORDER BY section_id,version DESC",
                courseId);
    }

    @PostMapping("/exam-papers/{id}/attempts")
    public ResponseEntity<OperationStore.View> start(
            @PathVariable long id, @RequestHeader("Idempotency-Key") String key) {
        UserContext.requireUser();
        var paper = service.coursePaper(id);
        Long lesson = learning.isLessonValid(((Number) paper.get("course_id")).longValue());
        if (lesson == null) throw new BadRequestException("无课程学习权限");
        return command(
                key, new Command("START", id, null, lesson, null, null, null, null, null, null));
    }

    @GetMapping("/exam-attempts/{id}")
    public Map<String, Object> attempt(@PathVariable long id) {
        return service.view(id, UserContext.requireUser(), false);
    }

    @GetMapping("/exam-attempts/page")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "分页或课程参数无效",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = R.class))),
        @ApiResponse(
                responseCode = "401",
                description = "请先登录",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = R.class)))
    })
    public Map<String, Object> history(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long courseId) {
        long user = UserContext.requireUser();
        if (pageNo < 1
                || pageNo > 10000
                || pageSize < 1
                || pageSize > 100
                || courseId != null && courseId <= 0) throw new BadRequestException("分页或课程参数无效");
        // Retained attempts belong to the student even after a refund or expiry.
        // Listing history never grants permission to start or submit an exam.
        String from = " FROM exam_attempt a JOIN exam_paper p ON p.id=a.paper_id WHERE a.user_id=? AND a.class_activity_id IS NULL";
        List<Object> parameters = new ArrayList<>();
        parameters.add(user);
        if (courseId != null) {
            from += " AND p.course_id=?";
            parameters.add(courseId);
        }
        Long total =
                jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, parameters.toArray());
        parameters.add(pageSize);
        parameters.add((pageNo - 1) * pageSize);
        var list =
                jdbc.queryForList(
                        "SELECT a.id,a.paper_id,a.status,a.score,a.passed,a.created_at,a.submitted_at,a.graded_at,p.course_id,p.section_id,p.version AS paper_version,p.total_score"
                                + from
                                + " ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?",
                        parameters.toArray());
        return Map.of("total", total, "pageNo", pageNo, "pageSize", pageSize, "list", list);
    }

    @PostMapping("/exam-attempts/{id}/submit")
    public ResponseEntity<OperationStore.View> submit(
            @PathVariable long id,
            @RequestBody Command form,
            @RequestHeader("Idempotency-Key") String key) {
        return command(
                key,
                new Command(
                        "SUBMIT",
                        null,
                        id,
                        null,
                        form.answers(),
                        null,
                        null,
                        form.version(),
                        null,
                        null));
    }

    @GetMapping("/teacher/exam-attempts")
    public List<Map<String, Object>> pending() {
        long user = UserContext.requireUser();
        Long role = UserContext.getRole();
        if (!Set.of(1L, 3L).contains(role == null ? 0L : role))
            throw new ForbiddenException("需要评分教师权限");
        return role == 1L
                ? jdbc.queryForList(
                        "SELECT a.*,p.course_id,p.section_id FROM exam_attempt a JOIN exam_paper p ON p.id=a.paper_id WHERE a.class_activity_id IS NULL AND status='WAIT_GRADING' ORDER BY submitted_at LIMIT 100")
                : jdbc.queryForList(
                        "SELECT a.*,p.course_id,p.section_id FROM exam_attempt a JOIN exam_paper p ON p.id=a.paper_id JOIN exam_grader g ON g.paper_id=a.paper_id WHERE g.user_id=? AND a.class_activity_id IS NULL AND a.status='WAIT_GRADING' ORDER BY submitted_at LIMIT 100",
                        user);
    }

    public record PendingExamPage(List<Map<String, Object>> list, long total, int pageNo,
                                  int pageSize, List<Long> courseIds, LocalDateTime oldestPendingAt) {}

    private record PendingExamSummary(long count, LocalDateTime oldest) {}

    @GetMapping("/teacher/exam-attempts/page")
    @Transactional(readOnly = true)
    public PendingExamPage pendingPage(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long studentId) {
        long user = UserContext.requireUser();
        Long role = UserContext.getRole();
        if (!Set.of(1L, 3L).contains(role == null ? 0L : role))
            throw new ForbiddenException("需要评分教师权限");
        if (pageNo < 1
                || pageNo > 10000
                || pageSize < 1
                || pageSize > 100
                || courseId != null && courseId <= 0
                || studentId != null && studentId <= 0)
            throw new BadRequestException("分页范围无效");
        String from =
                " FROM exam_attempt a JOIN exam_paper p ON p.id=a.paper_id WHERE a.class_activity_id IS NULL AND a.status='WAIT_GRADING'";
        List<Object> parameters = new ArrayList<>();
        if (role != 1L) {
            from +=
                    " AND EXISTS(SELECT 1 FROM exam_grader g WHERE g.paper_id=a.paper_id AND g.user_id=?)";
            parameters.add(user);
        }
        var courseIds =
                courseId == null
                        ? jdbc.queryForList(
                                "SELECT DISTINCT p.course_id" + from + " ORDER BY p.course_id",
                                Long.class,
                                parameters.toArray())
                        : List.<Long>of();
        if (courseId != null) {
            from += " AND p.course_id=?";
            parameters.add(courseId);
        }
        if (studentId != null) {
            from += " AND a.user_id=?";
            parameters.add(studentId);
        }
        var summary = jdbc.queryForObject("SELECT COUNT(*) AS total, MIN(a.submitted_at) AS oldest" + from,
                (rs, row) -> new PendingExamSummary(rs.getLong("total"), rs.getObject("oldest", LocalDateTime.class)),
                parameters.toArray());
        parameters.add(pageSize);
        parameters.add((pageNo - 1) * pageSize);
        var items = jdbc.queryForList(
                "SELECT a.*,p.course_id,p.section_id" + from + " ORDER BY a.submitted_at,a.id LIMIT ? OFFSET ?",
                parameters.toArray());
        return new PendingExamPage(items, summary.count(), pageNo, pageSize, courseIds, summary.oldest());
    }

    @GetMapping("/teacher/exam-attempts/{id}")
    public Map<String, Object> grading(@PathVariable long id) {
        long user = UserContext.requireUser();
        Long role = UserContext.getRole();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM exam_attempt WHERE id=? AND class_activity_id IS NULL",
                Integer.class, id) == 0) throw new BadRequestException("考试记录不存在");
        if (!Long.valueOf(1).equals(role)
                && (!Long.valueOf(3).equals(role)
                        || jdbc.queryForList(
                                        "SELECT g.user_id FROM exam_grader g JOIN exam_attempt a ON a.paper_id=g.paper_id WHERE a.id=? AND g.user_id=?",
                                        id,
                                        user)
                                .isEmpty())) throw new ForbiddenException("没有评分权限");
        return service.view(id, user, true);
    }

    /** Grading assignment is deliberately independent from permission to inspect taught-course learning. */
    @GetMapping("/teacher/exam-statistics")
    public Map<String, Object> statistics(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long studentId) {
        long user = UserContext.requireUser();
        Long role = UserContext.getRole();
        if (!Set.of(1L, 3L).contains(role == null ? 0L : role))
            throw new ForbiddenException("需要评分教师权限");
        if (pageNo < 1
                || pageNo > 10000
                || pageSize < 1
                || pageSize > 100
                || courseId != null && courseId <= 0
                || studentId != null && studentId <= 0) throw new BadRequestException("统计筛选参数无效");
        String attempts = " FROM exam_attempt a JOIN exam_paper p ON p.id=a.paper_id AND a.class_activity_id IS NULL";
        String filters = "";
        List<Object> args = new ArrayList<>();
        if (role != 1L) {
            filters +=
                    " AND EXISTS(SELECT 1 FROM exam_grader g WHERE g.paper_id=p.id AND g.user_id=?)";
            args.add(user);
        }
        if (courseId != null) {
            filters += " AND p.course_id=?";
            args.add(courseId);
        }
        if (studentId != null) {
            filters += " AND a.user_id=?";
            args.add(studentId);
        }
        var commonMistakes =
                jdbc.queryForList(
                        "WITH scoped AS (SELECT p.course_id,a.user_id,a.graded_at,a.id AS attempt_id,pq.question_id,pq.name"
                                + attempts
                                + " JOIN exam_answer r ON r.attempt_id=a.id JOIN exam_paper_question pq ON pq.paper_id=p.id AND pq.question_id=r.question_id"
                                + " WHERE a.status='FINISHED' AND r.score<pq.score"
                                + filters
                                + "),"
                                + " ranked AS (SELECT *,ROW_NUMBER() OVER(PARTITION BY course_id,question_id ORDER BY graded_at DESC,attempt_id DESC) AS latest FROM scoped)"
                                + " SELECT course_id,question_id,MAX(CASE WHEN latest=1 THEN name END) AS name,COUNT(DISTINCT user_id) AS students"
                                + " FROM ranked GROUP BY course_id,question_id ORDER BY students DESC,course_id,question_id LIMIT 10",
                        args.toArray());
        String from = attempts + " WHERE 1=1" + filters;
        String groups = " GROUP BY p.course_id,a.user_id";
        Long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM(SELECT p.course_id,a.user_id"
                                + from
                                + groups
                                + ") grouped",
                        Long.class,
                        args.toArray());
        args.add(pageSize);
        args.add((pageNo - 1) * pageSize);
        var rows =
                jdbc.queryForList(
                        "SELECT p.course_id,a.user_id AS student_id,COUNT(*) AS attempts,SUM(a.status='WAIT_GRADING') AS pending_grading,SUM(a.status='FINISHED') AS finished,SUM(a.status='FINISHED' AND a.passed=1) AS passed,ROUND(100.0*SUM(a.status='FINISHED' AND a.passed=1)/NULLIF(SUM(a.status='FINISHED'),0),1) AS pass_rate,MAX(a.submitted_at) AS last_submitted_at"
                                + from
                                + groups
                                + " ORDER BY p.course_id,a.user_id LIMIT ? OFFSET ?",
                        args.toArray());
        return Map.of(
                "scope",
                "GRADING_ASSIGNMENT",
                "commonMistakes",
                commonMistakes,
                "total",
                total,
                "pageNo",
                pageNo,
                "pageSize",
                pageSize,
                "list",
                rows);
    }

    @PostMapping("/teacher/exam-attempts/{id}/grades")
    public ResponseEntity<OperationStore.View> grade(
            @PathVariable long id,
            @RequestBody Command form,
            @RequestHeader("Idempotency-Key") String key) {
        return command(
                key,
                new Command(
                        "GRADE",
                        null,
                        id,
                        null,
                        null,
                        form.questionId(),
                        form.score(),
                        form.version(),
                        form.feedback(),
                        UserContext.getRole()));
    }

    private ResponseEntity<OperationStore.View> command(String key, Command cmd) {
        return ResponseEntity.accepted()
                .body(operations.submit(UserContext.requireUser(), "EXAM_COMMAND", key, cmd));
    }
}
