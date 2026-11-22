package com.zhixuetang.message.handler;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhixuetang.common.autoconfigure.reliability.InboxStore;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.*;

@Component
@RequiredArgsConstructor
public class LearningNoticeListener {
    public enum Target { QUESTION, EXAM, ORDER, PRACTICE, PRACTICE_CORRECTION }

    public record Notice(
            String noticeId, Long userId, String title, String content, Long bizId,
            Target targetType, Long targetId, Long courseId, Long sectionId) {}

    private final InboxStore inbox;
    private final JdbcTemplate jdbc;

    @RabbitListener(
            bindings =
                    @QueueBinding(
                            value = @Queue(value = "learning.notice.inbox", durable = "true"),
                            exchange = @Exchange(value = "learning.topic", type = "topic"),
                            key = {"learning.notice.created", "learning.notice.product"}))
    public void receive(Notice notice) {
        if (notice.userId() == null
                || notice.noticeId() == null
                || notice.title() == null
                || notice.content() == null
                || (notice.targetType() != null && notice.targetId() == null)
                || (notice.targetType() == Target.QUESTION && notice.courseId() == null)
                || (notice.targetType() == Target.PRACTICE_CORRECTION
                        && (notice.courseId() == null || notice.courseId() <= 0
                                || notice.targetId() < 2 || notice.targetId() > Integer.MAX_VALUE)))
            throw new IllegalArgumentException("Invalid learning notice");
        inbox.once(
                "learning.notice",
                notice.noticeId(),
                () -> {
                    LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
                    jdbc.update(
                            """
                            INSERT INTO user_inbox
                                (id,user_id,type,title,content,is_read,publisher,push_time,expire_time,
                                 target_type,target_id,course_id,section_id)
                            VALUES(?,?,?,?,?,0,0,?,?,?,?,?,?)
                            """,
                            IdWorker.getId(),
                            notice.userId(),
                            notice.targetType() == null || notice.targetType() == Target.QUESTION ? 2 : 3,
                            notice.title(),
                            notice.content(),
                            now,
                            now.plusMonths(6),
                            notice.targetType() == null ? null : notice.targetType().name(),
                            notice.targetId(),
                            notice.courseId(),
                            notice.sectionId());
                });
    }
}
