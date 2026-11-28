package com.zhixuetang.api.client.learning;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.time.LocalDateTime;

@FeignClient(contextId = "classroom", value = "learning-service")
public interface ClassroomClient {
    record Seat(long classId, long courseId, long userId, long orderId) {}
    record ExamAccessRequest(long activityId, long userId, Long roleId, String action, Long memberId) {}
    record ExamAccess(Long classId, Long paperId, Long courseId, Long memberId, Long lessonId,
                      int maxAttempts, LocalDateTime closesAt, boolean solutionsAvailable) {}

    @PostMapping("/internal/v1/learning/classrooms/seats")
    void reserve(@RequestBody Seat seat);

    @PostMapping("/internal/v1/learning/classrooms/exam-access")
    ExamAccess examAccess(@RequestBody ExamAccessRequest request);
}
