package com.tianji.user.privacy;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Samples durable task states; an unsuccessful sample never refreshes health evidence. */
@Component
public final class PrivacyTaskMetrics {
    private record Snapshot(
            long pending, long failed, long approval, long oldestSeconds, long lastSuccess) {}

    private final JdbcTemplate jdbc;
    private volatile Snapshot snapshot = new Snapshot(0, 0, 0, 0, 0);

    public PrivacyTaskMetrics(JdbcTemplate jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        meters.gauge("tj.privacy.tasks.pending", this, value -> value.snapshot.pending());
        meters.gauge("tj.privacy.tasks.failed", this, value -> value.snapshot.failed());
        meters.gauge(
                "tj.privacy.tasks.approval.required", this, value -> value.snapshot.approval());
        meters.gauge(
                "tj.privacy.tasks.oldest.seconds", this, value -> value.snapshot.oldestSeconds());
        meters.gauge(
                "tj.privacy.metrics.last.success.timestamp.seconds",
                this,
                value -> value.snapshot.lastSuccess());
    }

    @Scheduled(fixedDelayString = "${tj.privacy.metrics-interval-ms:5000}")
    public void refresh() {
        var row =
                jdbc.queryForMap(
                        "SELECT COALESCE(SUM(status='PENDING'),0) pending,COALESCE(SUM(status='FAILED'),0) failed,COALESCE(SUM(status='APPROVAL_REQUIRED'),0) approval,COALESCE(TIMESTAMPDIFF(SECOND,MIN(IF(status='PENDING',created_at,NULL)),NOW(3)),0) age FROM privacy_task WHERE status IN('PENDING','FAILED','APPROVAL_REQUIRED')");
        snapshot =
                new Snapshot(
                        ((Number) row.get("pending")).longValue(),
                        ((Number) row.get("failed")).longValue(),
                        ((Number) row.get("approval")).longValue(),
                        Math.max(0, ((Number) row.get("age")).longValue()),
                        java.time.Instant.now().getEpochSecond());
    }
}
