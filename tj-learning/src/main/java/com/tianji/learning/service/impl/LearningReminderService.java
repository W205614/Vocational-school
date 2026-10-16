package com.tianji.learning.service.impl;

import com.tianji.api.client.exam.ExamClient;
import com.tianji.api.client.message.LearningNotificationClient;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LearningReminderService {
    private final JdbcTemplate jdbc;
    private final LearningEntitlementService entitlements;
    private final ExamClient exams;
    private final LearningNotificationClient notifications;

    public Map<String,Object> overview() {
        long user=UserContext.requireUser();var result=new LinkedHashMap<String,Object>();
        result.put("pendingExams",null);result.put("waitingForGradingExams",null);result.put("examReminderError",null);
        result.put("unreadReplies",null);result.put("replyReminderError",null);
        try {
            // Do not use a paginated lesson view, stale lesson expiry, or plan membership.
            var courses=jdbc.queryForList("SELECT DISTINCT course_id FROM learning_lesson WHERE user_id=? ORDER BY course_id",Long.class,user);
            int pending=0,waiting=0;
            for(int start=0;start<courses.size();start+=100) {
                var active=new ArrayList<>(entitlements.summaries(user,courses.subList(start,Math.min(courses.size(),start+100))).keySet());
                if(active.isEmpty())continue;
                var counts=exams.learningOverview(active);
                pending=Math.addExact(pending,count(counts==null?null:counts.get("pendingExams")));
                waiting=Math.addExact(waiting,count(counts.get("waitingForGradingExams")));
            }
            result.put("pendingExams",pending);result.put("waitingForGradingExams",waiting);
        } catch(RuntimeException unavailable) {
            result.put("examReminderError","考试提醒暂不可用，请重试");
        }
        try {result.put("unreadReplies",count(notifications.unreadInteractions()));}
        catch(RuntimeException unavailable) {result.put("replyReminderError","互动消息提醒暂不可用，请重试");}
        return result;
    }
    private static int count(Integer value) {
        if(value==null || value<0)throw new IllegalStateException("Invalid reminder count");return value;
    }
}
