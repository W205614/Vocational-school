package com.tianji.message.controller;

import com.tianji.common.utils.InternalAuth;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/inboxes")
public class InternalLearningInboxController {
    private final JdbcTemplate jdbc;

    /** Type 2 includes replies and accepted-answer notifications, not only replies.
     * Include public interactions not yet materialized by the paginated inbox reader;
     * an existing read receipt suppresses that public item, so it is never counted twice. */
    @GetMapping("/unread-interactions") public Integer unreadInteractions() {
        InternalAuth.requireService();long user=UserContext.requireUser();
        LocalDateTime now=LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        return jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM user_inbox WHERE user_id=? AND type=2 AND is_read=0 AND push_time<=? AND expire_time>?) + (SELECT COUNT(*) FROM public_notice p WHERE p.type=2 AND p.push_time<=? AND p.expire_time>? AND NOT EXISTS(SELECT 1 FROM user_inbox i WHERE i.user_id=? AND i.public_notice_id=p.id))",Integer.class,user,now,now,now,now,user);
    }
}
