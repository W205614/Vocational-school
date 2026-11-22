package com.zhixuetang.api.dto.exam;

import java.time.LocalDateTime;

public record ExamLearningOverview(
        Integer pendingExams,
        Integer waitingForGradingExams,
        ActiveAttempt activeAttempt,
        PendingReview pendingReview) {
    public record ActiveAttempt(
            Long attemptId, Long courseId, Long sectionId, LocalDateTime updatedAt) {}

    public record PendingReview(
            Long attemptId,
            Long questionId,
            Long courseId,
            Long sectionId,
            long failedAttempts,
            LocalDateTime lastReviewedAt) {}
}
