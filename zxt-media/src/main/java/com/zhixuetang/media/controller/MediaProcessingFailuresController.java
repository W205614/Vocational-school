package com.zhixuetang.media.controller;

import com.zhixuetang.common.domain.query.PageQuery;
import com.zhixuetang.common.utils.UserContext;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MediaProcessingFailuresController {
    private final JdbcTemplate jdbc;
    private final Environment environment;

    public record Failure(String taskId, String kind, String subject, String errorCode, Long version,
                          LocalDateTime since, long waitSeconds, boolean canRetry) {}
    public record Page(List<Failure> list, long total) {}

    @GetMapping("/api/v2/admin/media/processing-failures")
    public Page failures(PageQuery page) {
        UserContext.requireAdmin();
        long actor = UserContext.requireUser();
        boolean cloudEnabled = environment.getProperty("zxt.cloud-media.enabled", Boolean.class, false);
        int offset = page.from();
        var rows = jdbc.query("""
                SELECT task_id,kind,subject,error_code,version,since,can_retry,
                       GREATEST(0,TIMESTAMPDIFF(SECOND,since,NOW(3))) AS wait_seconds
                FROM (
                    SELECT id AS task_id,'TRIAL' AS kind,CAST(media_id AS CHAR) AS subject,error_code,
                           version,created_at AS since,TRUE AS can_retry FROM media_trial WHERE state='FAILED'
                    UNION ALL
                    SELECT id,'UPLOAD',filename,error_code,NULL,updated_at,? AND owner_id=? AND attempts<3
                    FROM cloud_media_upload WHERE state='FAILED'
                ) failed
                ORDER BY since,task_id LIMIT ? OFFSET ?
                """, (rs, row) -> new Failure(rs.getString("task_id"), rs.getString("kind"),
                        rs.getString("subject"), rs.getString("error_code"), rs.getObject("version", Long.class),
                        rs.getObject("since", LocalDateTime.class), rs.getLong("wait_seconds"), rs.getBoolean("can_retry")),
                cloudEnabled, actor, page.getPageSize(), offset);
        long total = jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM media_trial WHERE state='FAILED')+
                       (SELECT COUNT(*) FROM cloud_media_upload WHERE state='FAILED')
                """, Long.class);
        return new Page(rows, total);
    }
}
