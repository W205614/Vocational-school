package com.zhixuetang.api.client.learning;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(contextId = "classroomExam", value = "exam-service")
public interface ClassroomExamClient {
    record Paper(Long id, Long courseId, Long sectionId, int version, int totalScore) {}
    record ExamResult(Long memberId, Long userId, Integer bestPercent, boolean pending) {}

    @GetMapping("/internal/v1/exam/classroom-papers")
    List<Paper> papers(@RequestParam("courseId") long courseId);

    @GetMapping("/internal/v1/exam/classroom-activities/{id}/results")
    List<ExamResult> results(@PathVariable("id") long activityId);
}
