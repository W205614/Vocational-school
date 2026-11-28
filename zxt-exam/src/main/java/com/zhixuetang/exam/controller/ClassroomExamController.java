package com.zhixuetang.exam.controller;

import com.zhixuetang.api.client.learning.ClassroomClient;
import com.zhixuetang.api.client.learning.ClassroomClient.ExamAccessRequest;
import com.zhixuetang.api.client.learning.ClassroomExamClient.ExamResult;
import com.zhixuetang.api.client.learning.ClassroomExamClient.Paper;
import com.zhixuetang.common.autoconfigure.reliability.OperationStore;
import com.zhixuetang.common.exceptions.ForbiddenException;
import com.zhixuetang.common.utils.InternalAuth;
import com.zhixuetang.common.utils.UserContext;
import com.zhixuetang.exam.service.impl.ExamWorkflowService;
import com.zhixuetang.exam.service.impl.ExamWorkflowService.Command;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ClassroomExamController {
    public record Attempt(Long id, Long memberId, String status, LocalDateTime createdAt) {}

    private final JdbcTemplate jdbc;
    private final ClassroomClient classrooms;
    private final OperationStore operations;
    private final ExamWorkflowService workflow;

    @PostMapping("/api/v2/classroom-exams/{activity}/attempts")
    public ResponseEntity<OperationStore.View> start(@PathVariable long activity,
                                                    @RequestHeader("Idempotency-Key") String key) {
        long user = student();
        classrooms.examAccess(new ExamAccessRequest(activity, user, 2L, "OPEN", null));
        var cmd = new Command("START", null, null, null, null, null, null, null, null, null, activity);
        return ResponseEntity.accepted().body(operations.submit(user, "EXAM_COMMAND", key, cmd));
    }

    @GetMapping("/api/v2/classroom-exams/{activity}/attempts")
    public List<Attempt> mine(@PathVariable long activity) {
        return jdbc.query("""
                SELECT id,class_member_id,status,created_at FROM exam_attempt
                WHERE class_activity_id=? AND user_id=? ORDER BY created_at DESC,id DESC
                """, (rs, row) -> new Attempt(rs.getLong(1), rs.getLong(2), rs.getString(3),
                rs.getObject(4, LocalDateTime.class)), activity, student());
    }

    @GetMapping("/api/v2/teacher/classroom-exams/{activity}/attempts")
    public List<Map<String, Object>> pending(@PathVariable long activity) {
        classrooms.examAccess(new ExamAccessRequest(activity, UserContext.requireUser(), UserContext.getRole(),
                "TEACHING", null));
        return jdbc.queryForList("""
                SELECT id,user_id,class_member_id,status,submitted_at FROM exam_attempt
                WHERE class_activity_id=? AND status='WAIT_GRADING' ORDER BY submitted_at,id
                """, activity);
    }

    @GetMapping("/api/v2/teacher/classroom-exam-attempts/{id}")
    public Map<String, Object> attempt(@PathVariable long id) {
        return workflow.viewClassGrade(id, UserContext.requireUser(), UserContext.getRole());
    }

    @Hidden
    @GetMapping("/internal/v1/exam/classroom-papers")
    public List<Paper> papers(@RequestParam long courseId) {
        InternalAuth.requireService();
        return jdbc.query("""
                SELECT id,course_id,section_id,version,total_score FROM exam_paper
                WHERE course_id=? AND classroom_only=TRUE ORDER BY section_id,version DESC
                """, (rs, row) -> new Paper(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getInt(4), rs.getInt(5)),
                courseId);
    }

    @Hidden
    @GetMapping("/internal/v1/exam/classroom-activities/{id}/results")
    public List<ExamResult> results(@PathVariable long id) {
        InternalAuth.requireService();
        return jdbc.query("""
                SELECT a.class_member_id, a.user_id,
                ROUND(MAX(CASE WHEN a.status='FINISHED' THEN 100.0*a.score/p.total_score END)) AS best,
                SUM(a.status='WAIT_GRADING')>0 AS pending
                FROM exam_attempt a JOIN exam_paper p ON p.id=a.paper_id
                WHERE a.class_activity_id=? GROUP BY a.class_member_id,a.user_id
                """, (rs, row) -> new ExamResult(rs.getLong(1), rs.getLong(2), rs.getObject(3, Integer.class),
                rs.getBoolean(4)), id);
    }

    private static long student() {
        long user = UserContext.requireUser();
        if (!Long.valueOf(2).equals(UserContext.getRole())) throw new ForbiddenException("需要学生账号");
        return user;
    }
}
