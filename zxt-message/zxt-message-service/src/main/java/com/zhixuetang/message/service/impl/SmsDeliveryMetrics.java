package com.zhixuetang.message.service.impl;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SmsDeliveryMetrics {
    private final JdbcTemplate jdbc;
    private volatile long pending, accepted, unknown, failed, expired, lastSuccess;

    public SmsDeliveryMetrics(JdbcTemplate jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        meters.gauge("zxt.sms.pending", this, v -> v.pending);
        meters.gauge("zxt.sms.accepted", this, v -> v.accepted);
        meters.gauge("zxt.sms.unknown", this, v -> v.unknown);
        meters.gauge("zxt.sms.failed", this, v -> v.failed);
        meters.gauge("zxt.sms.expired", this, v -> v.expired);
        meters.gauge("zxt.sms.metrics.last.success.timestamp.seconds", this, v -> v.lastSuccess);
    }

    @Scheduled(fixedDelay = 5000)
    public void refresh() {
        var row =
                jdbc.queryForMap(
                        "SELECT COALESCE(SUM(state IN('PENDING','SUBMITTING')),0) pending,COALESCE(SUM(state='ACCEPTED'),0) accepted,COALESCE(SUM(state='UNKNOWN' OR state='SUBMITTING' AND lease_until<NOW(3)),0) unknown,COALESCE(SUM(state='FAILED'),0) failed,COALESCE(SUM(state='EXPIRED'),0) expired FROM sms_delivery_recipient");
        pending = ((Number) row.get("pending")).longValue();
        accepted = ((Number) row.get("accepted")).longValue();
        unknown = ((Number) row.get("unknown")).longValue();
        failed = ((Number) row.get("failed")).longValue();
        expired = ((Number) row.get("expired")).longValue();
        lastSuccess = java.time.Instant.now().getEpochSecond();
    }
}
