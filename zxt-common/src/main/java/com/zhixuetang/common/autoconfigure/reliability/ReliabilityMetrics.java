package com.zhixuetang.common.autoconfigure.reliability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

public class ReliabilityMetrics {
    private final JdbcTemplate jdbc;
    private volatile long pending,
            failed,
            operations,
            consumerFailures,
            oldestAge,
            operationFailures,
            operationAge,
            lastSuccess;

    public ReliabilityMetrics(JdbcTemplate jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        meters.gauge("zxt.events.pending", this, v -> v.pending);
        meters.gauge("zxt.events.dead", this, v -> v.failed);
        meters.gauge("zxt.operations.pending", this, v -> v.operations);
        meters.gauge("zxt.consumer.failures", this, v -> v.consumerFailures);
        meters.gauge("zxt.events.oldest.seconds", this, v -> v.oldestAge);
        meters.gauge("zxt.operations.retry.exhausted", this, v -> v.operationFailures);
        meters.gauge("zxt.operations.oldest.seconds", this, v -> v.operationAge);
        meters.gauge(
                "zxt.reliability.metrics.last.success.timestamp.seconds", this, v -> v.lastSuccess);
    }

    @Scheduled(fixedDelayString = "${zxt.reliability.metrics-interval-ms:5000}")
    public void refresh() {
        var events =
                jdbc.queryForMap(
                        "SELECT COALESCE(SUM(status IN('PENDING','SENDING')),0) pending,COALESCE(SUM(status='DEAD'),0) failed,COALESCE(TIMESTAMPDIFF(SECOND,MIN(IF(status IN('PENDING','SENDING') AND DATE_ADD(created_at,INTERVAL delay_ms*1000 MICROSECOND)<=CURRENT_TIMESTAMP(3),DATE_ADD(created_at,INTERVAL delay_ms*1000 MICROSECOND),NULL)),CURRENT_TIMESTAMP(3)),0) age FROM reliability_outbox WHERE status IN('PENDING','SENDING','DEAD')");
        var requests =
                jdbc.queryForMap(
                        "SELECT COALESCE(SUM(status='PENDING'),0) pending,COALESCE(SUM(status='FAILED' AND error_code='RETRY_EXHAUSTED'),0) failed,COALESCE(TIMESTAMPDIFF(SECOND,MIN(IF(status='PENDING',created_at,NULL)),CURRENT_TIMESTAMP(3)),0) age FROM reliability_operation WHERE status IN('PENDING','FAILED')");
        long consumers =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM reliability_consumer_failure WHERE status='FAILED'",
                        Long.class);
        // Publish only after every query succeeds; a scrape must not present a partial refresh as
        // current.
        pending = ((Number) events.get("pending")).longValue();
        failed = ((Number) events.get("failed")).longValue();
        oldestAge = ((Number) events.get("age")).longValue();
        operations = ((Number) requests.get("pending")).longValue();
        operationFailures = ((Number) requests.get("failed")).longValue();
        operationAge = Math.max(0, ((Number) requests.get("age")).longValue());
        consumerFailures = consumers;
        lastSuccess = java.time.Instant.now().getEpochSecond();
    }
}
