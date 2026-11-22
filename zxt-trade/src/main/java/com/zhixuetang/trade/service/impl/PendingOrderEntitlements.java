package com.zhixuetang.trade.service.impl;

import com.zhixuetang.api.dto.trade.OrderEntitlementLookup.Line;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PendingOrderEntitlements {
    private static final int PAGE_SIZE = 20;
    private static final int LOOKUP_LIMIT = 100;
    private final JdbcTemplate jdbc;
    private final OrderEntitlementProgress progress;

    public record Cursor(LocalDateTime paidAt, Long detailId) {}
    public record PendingLine(Long orderId, Long detailId, Long courseId, String courseName,
                              LocalDateTime paidAt, long waitSeconds) {}
    public record Page(List<PendingLine> list, Cursor next) {}
    private record Candidate(PendingLine row, long userId) {}

    public Page page(Cursor after) {
        var missing = new ArrayList<PendingLine>(PAGE_SIZE);
        // Trade owns payment and learning owns the grant. Traverse paid lines in stable batches,
        // keeping a continuation for later batches even when every line in a batch has synced.
        for (int batch = 0; batch < 5; batch++) {
            var candidates = paidLines(after);
            int inspected = Math.min(candidates.size(), LOOKUP_LIMIT);
            if (inspected == 0) return new Page(missing, null);
            var statuses = progress.lookup(candidates.subList(0, inspected).stream()
                    .map(candidate -> new Line(candidate.row().detailId(), candidate.userId(), candidate.row().courseId()))
                    .toList());
            for (int index = 0; index < inspected; index++) {
                var row = candidates.get(index).row();
                after = new Cursor(row.paidAt(), row.detailId());
                if (statuses.get(row.detailId()).recordedAt() == null) missing.add(row);
                if (missing.size() == PAGE_SIZE)
                    return new Page(missing, index + 1 < candidates.size() ? after : null);
            }
            if (candidates.size() <= LOOKUP_LIMIT) return new Page(missing, null);
        }
        return new Page(missing, after);
    }

    private List<Candidate> paidLines(Cursor after) {
        var sql = """
                SELECT o.id AS order_id,d.id AS detail_id,d.user_id,d.course_id,d.name,
                       COALESCE(o.pay_time,o.finish_time) AS paid_at,
                       GREATEST(0,TIMESTAMPDIFF(SECOND,COALESCE(o.pay_time,o.finish_time),NOW(3))) AS wait_seconds
                FROM `order` o JOIN order_detail d ON d.order_id=o.id
                WHERE o.status IN(2,4,6) AND d.status IN(2,4,6)
                  AND COALESCE(o.pay_time,o.finish_time) IS NOT NULL
                """;
        var parameters = new ArrayList<Object>();
        if (after != null) {
            sql += " AND (COALESCE(o.pay_time,o.finish_time),d.id)>(?,?)";
            parameters.add(after.paidAt());
            parameters.add(after.detailId());
        }
        sql += " ORDER BY paid_at,d.id LIMIT ?";
        parameters.add(LOOKUP_LIMIT + 1);
        return jdbc.query(sql, (rs, index) -> new Candidate(new PendingLine(
                        rs.getLong("order_id"), rs.getLong("detail_id"), rs.getLong("course_id"), rs.getString("name"),
                        rs.getObject("paid_at", LocalDateTime.class), rs.getLong("wait_seconds")), rs.getLong("user_id")),
                parameters.toArray());
    }
}
