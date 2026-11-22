package com.zhixuetang.learning.service.impl;

import com.zhixuetang.api.client.exam.ExamClient;
import com.zhixuetang.api.client.message.LearningNotificationClient;
import com.zhixuetang.api.dto.exam.ExamLearningOverview.ActiveAttempt;
import com.zhixuetang.api.dto.exam.ExamLearningOverview.PendingReview;
import com.zhixuetang.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LearningReminderService {
    private static final Comparator<ActiveAttempt> ATTEMPT_ORDER =
            Comparator.comparing(ActiveAttempt::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ActiveAttempt::attemptId, Comparator.reverseOrder());
    private static final Comparator<PendingReview> REVIEW_ORDER =
            Comparator.comparingLong(PendingReview::failedAttempts).reversed()
                    .thenComparing(PendingReview::lastReviewedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(PendingReview::attemptId, Comparator.reverseOrder())
                    .thenComparing(PendingReview::questionId);

    private final JdbcTemplate jdbc;
    private final LearningEntitlementService entitlements;
    private final ExamClient exams;
    private final LearningNotificationClient notifications;

    public Map<String, Object> overview() {
        long user = UserContext.requireUser();
        var result = new LinkedHashMap<String, Object>();
        result.put("pendingExams", null);
        result.put("waitingForGradingExams", null);
        result.put("activeAttempt", null);
        result.put("pendingReview", null);
        result.put("examReminderError", null);
        result.put("unreadReplies", null);
        result.put("replyReminderError", null);
        try {
            // Do not use a paginated lesson view, stale lesson expiry, or plan membership.
            var courses =
                    jdbc.queryForList(
                            "SELECT DISTINCT course_id FROM learning_lesson WHERE user_id=? ORDER BY course_id",
                            Long.class,
                            user);
            int pending = 0, waiting = 0;
            ActiveAttempt activeAttempt = null;
            PendingReview pendingReview = null;
            for (int start = 0; start < courses.size(); start += 100) {
                var active =
                        new ArrayList<>(
                                entitlements
                                        .summaries(
                                                user,
                                                courses.subList(
                                                        start,
                                                        Math.min(courses.size(), start + 100)))
                                        .keySet());
                if (active.isEmpty()) continue;
                var counts = exams.learningOverview(active);
                pending = Math.addExact(pending, count(counts == null ? null : counts.pendingExams()));
                waiting = Math.addExact(waiting, count(counts.waitingForGradingExams()));
                if (counts.activeAttempt() != null
                        && (activeAttempt == null || ATTEMPT_ORDER.compare(counts.activeAttempt(), activeAttempt) < 0))
                    activeAttempt = counts.activeAttempt();
                if (counts.pendingReview() != null
                        && (pendingReview == null || REVIEW_ORDER.compare(counts.pendingReview(), pendingReview) < 0))
                    pendingReview = counts.pendingReview();
            }
            result.put("pendingExams", pending);
            result.put("waitingForGradingExams", waiting);
            result.put("activeAttempt", activeAttempt);
            result.put("pendingReview", pendingReview);
        } catch (RuntimeException unavailable) {
            result.put("examReminderError", "考试提醒暂不可用，请重试");
        }
        try {
            result.put("unreadReplies", count(notifications.unreadInteractions()));
        } catch (RuntimeException unavailable) {
            result.put("replyReminderError", "互动消息提醒暂不可用，请重试");
        }
        return result;
    }

    private static int count(Integer value) {
        if (value == null || value < 0) throw new IllegalStateException("Invalid reminder count");
        return value;
    }
}
