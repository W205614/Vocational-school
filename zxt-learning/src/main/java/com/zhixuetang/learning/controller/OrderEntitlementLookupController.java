package com.zhixuetang.learning.controller;

import com.zhixuetang.api.dto.trade.OrderEntitlementLookup.Line;
import com.zhixuetang.api.dto.trade.OrderEntitlementLookup.Status;
import com.zhixuetang.common.exceptions.BadRequestException;
import com.zhixuetang.common.utils.InternalAuth;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/learning-entitlements")
public class OrderEntitlementLookupController {
    private final JdbcTemplate jdbc;

    @PostMapping("/order-details")
    public List<Status> orderEntitlements(@RequestBody List<Line> lines) {
        InternalAuth.requireService();
        if (lines.isEmpty() || lines.size() > 100) throw new BadRequestException("订单条目数量无效");
        var ids = new HashSet<Long>();
        var parameters = new ArrayList<Object>(lines.size() * 3);
        for (Line line : lines) {
            if (line == null || line.detailId() <= 0 || line.userId() <= 0 || line.courseId() <= 0
                    || !ids.add(line.detailId())) throw new BadRequestException("订单条目无效");
            parameters.add(line.detailId());
            parameters.add(line.userId());
            parameters.add(line.courseId());
        }
        var rows = String.join(" UNION ALL ", Collections.nCopies(lines.size(),
                "SELECT CAST(? AS SIGNED) AS detail_id, CAST(? AS SIGNED) AS user_id, CAST(? AS SIGNED) AS course_id"));
        return jdbc.query("""
                SELECT line.detail_id, e.created_at, e.expires_at, e.active,
                       e.active=1 AND (e.expires_at IS NULL OR e.expires_at>NOW(3)) AS unexpired,
                       EXISTS(SELECT 1 FROM learning_entitlement other
                         WHERE other.user_id=line.user_id AND other.course_id=line.course_id
                           AND other.order_detail_id<>line.detail_id AND other.active=1
                           AND (other.expires_at IS NULL OR other.expires_at>NOW(3))) AS other_active
                FROM (%s) line LEFT JOIN learning_entitlement e
                  ON e.order_detail_id=line.detail_id AND e.user_id=line.user_id AND e.course_id=line.course_id
                """.formatted(rows),
                (rs, row) -> new Status(rs.getLong("detail_id"),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getObject("expires_at", LocalDateTime.class), rs.getBoolean("active"),
                        rs.getBoolean("unexpired"), rs.getBoolean("other_active")), parameters.toArray());
    }
}
