package com.zhixuetang.learning.controller;

import com.zhixuetang.common.domain.query.PageQuery;
import com.zhixuetang.common.exceptions.ForbiddenException;
import com.zhixuetang.common.utils.UserContext;
import com.zhixuetang.learning.service.impl.PracticeAssignmentService;
import com.zhixuetang.learning.service.impl.PracticeAssignmentService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2")
public class PracticeAssignmentController {
    private final PracticeAssignmentService practice;

    @GetMapping("/admin/learning-hub/practice/{courseId}")
    public AssignmentView configuration(@PathVariable long courseId) {
        UserContext.requireAdmin();
        return practice.configuration(courseId);
    }

    @PutMapping("/admin/learning-hub/practice/{courseId}")
    public Assignment configure(@PathVariable long courseId, @RequestBody Configuration form) {
        UserContext.requireAdmin();
        return practice.configure(courseId, UserContext.requireUser(), form);
    }

    @PutMapping("/admin/learning-hub/practice/{courseId}/corrections")
    public AssignmentView correct(@PathVariable long courseId, @RequestBody Correction form) {
        UserContext.requireAdmin();
        return practice.correct(courseId, UserContext.requireUser(), form);
    }

    @GetMapping("/learning-hub/practice")
    public List<StudentAssignment> assignments() {
        return practice.assignments(student());
    }

    @GetMapping("/learning-hub/practice/{courseId}")
    public StudentView assignment(@PathVariable long courseId) {
        return practice.student(courseId, student());
    }

    @PostMapping("/learning-hub/practice/{courseId}/submissions")
    public Submission submit(@PathVariable long courseId, @RequestBody Submit form) {
        return practice.submit(courseId, student(), form);
    }

    @GetMapping("/teacher/learning-hub/practice/{courseId}")
    public AssignmentView teachingAssignment(@PathVariable long courseId) {
        return practice.teachingAssignment(courseId, UserContext.requireUser(), UserContext.getRole());
    }

    @GetMapping("/teacher/learning-hub/practice/{courseId}/submissions")
    public SubmissionPage teachingWork(@PathVariable long courseId, PageQuery page) {
        return practice.teachingWork(courseId, UserContext.requireUser(), UserContext.getRole(), page);
    }

    @GetMapping("/teacher/learning-hub/practice/{courseId}/submissions/{studentId}")
    public List<Submission> teachingHistory(@PathVariable long courseId, @PathVariable long studentId) {
        return practice.teachingHistory(courseId, studentId, UserContext.requireUser(), UserContext.getRole());
    }

    @PutMapping("/teacher/learning-hub/practice/{courseId}/submissions/{id}/feedback")
    public Submission feedback(@PathVariable long courseId, @PathVariable long id, @RequestBody Feedback form) {
        return practice.review(courseId, id, UserContext.requireUser(), UserContext.getRole(), form);
    }

    private long student() {
        long user = UserContext.requireUser();
        if (!Long.valueOf(2).equals(UserContext.getRole())) throw new ForbiddenException("需要学生账号");
        return user;
    }
}
