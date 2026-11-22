package com.zhixuetang.learning.controller;

import com.zhixuetang.api.client.course.CourseClient;
import com.zhixuetang.api.client.user.UserClient;
import com.zhixuetang.common.domain.query.PageQuery;
import com.zhixuetang.common.exceptions.ForbiddenException;
import com.zhixuetang.common.utils.UserContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TeacherPracticeQueueController {
    private final JdbcTemplate jdbc;
    private final CourseClient courses;
    private final UserClient users;

    public record PendingWork(
            Long submissionId, Long courseId, Long studentId, String studentName,
            String assignmentTitle, LocalDateTime submittedAt) {}

    public record PendingPage(List<PendingWork> list, long total, LocalDateTime oldestPendingAt) {}

    @GetMapping("/api/v2/teacher/learning-hub/practice/pending")
    @Transactional(readOnly = true)
    public PendingPage pending(@RequestParam(required = false) Long courseId, PageQuery page) {
        long teacher = UserContext.requireUser();
        if (!Long.valueOf(3).equals(UserContext.getRole()))
            throw new ForbiddenException("需要授课教师账号");

        int offset = page.from();
        var taught = courses.taughtCourseIds(teacher);
        if (courseId != null && !taught.contains(courseId))
            throw new ForbiddenException("需要该课程的授课权限");
        var scope = courseId == null ? taught : List.of(courseId);
        if (scope.isEmpty()) return new PendingPage(List.of(), 0, null);

        String courseSlots = String.join(",", Collections.nCopies(scope.size(), "?"));
        String from = """
                FROM learning_practice_submission s
                JOIN learning_practice_assignment_version v
                  ON v.course_id=s.course_id AND v.version=s.assignment_version
                WHERE s.status='SUBMITTED' AND s.cleaned_at IS NULL AND s.course_id IN (%s)
                  AND NOT EXISTS (
                      SELECT 1 FROM learning_practice_submission later
                      WHERE later.course_id=s.course_id AND later.user_id=s.user_id
                        AND later.revision>s.revision AND later.cleaned_at IS NULL
                  )
                """.formatted(courseSlots);
        var summary = jdbc.queryForObject(
                "SELECT COUNT(*) AS total, MIN(s.submitted_at) AS oldest " + from,
                (rs, n) -> new PendingPage(List.of(), rs.getLong("total"),
                        rs.getObject("oldest", LocalDateTime.class)), scope.toArray());
        if (offset >= summary.total()) return summary;
        var args = new ArrayList<Object>(scope);
        args.add(page.getPageSize());
        args.add(offset);
        var rows = jdbc.query(
                "SELECT s.id,s.course_id,s.user_id,v.title,s.submitted_at " + from
                        + " ORDER BY s.submitted_at,s.id LIMIT ? OFFSET ?",
                (rs, n) -> new PendingWork(rs.getLong("id"), rs.getLong("course_id"),
                        rs.getLong("user_id"), null, rs.getString("title"),
                        rs.getObject("submitted_at", LocalDateTime.class)),
                args.toArray());
        var names = new HashMap<Long, String>();
        users.queryUserByIds(rows.stream().map(PendingWork::studentId).distinct().toList())
                .forEach(user -> names.put(user.getId(), user.getName()));
        return new PendingPage(rows.stream().map(row -> new PendingWork(
                row.submissionId(), row.courseId(), row.studentId(),
                names.getOrDefault(row.studentId(), "学员 " + row.studentId()),
                row.assignmentTitle(), row.submittedAt())).toList(), summary.total(), summary.oldestPendingAt());
    }
}
