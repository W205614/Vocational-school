package com.tianji.pay.service.impl;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicLong;

/** Scrapes read memory; the independent refresh owns database IO. */
@Component
public class ProviderReconciliationMetrics {
    private final JdbcTemplate jdbc;
    private final AtomicLong dead=new AtomicLong(),anomalies=new AtomicLong();
    private final io.micrometer.core.instrument.Counter failures;
    public ProviderReconciliationMetrics(JdbcTemplate jdbc,MeterRegistry registry) {
        this.jdbc=jdbc;
        registry.gauge("tj.payment.reconciliation.dead",dead);
        registry.gauge("tj.payment.anomalies",anomalies);
        failures=registry.counter("tj.payment.metrics.refresh.failures");
    }
    @Scheduled(fixedDelayString="${tj.pay.metrics-interval-ms:15000}") public void refresh() {
        try {
            long nextDead=jdbc.queryForObject("SELECT COUNT(*) FROM provider_payment_reconcile WHERE status='DEAD'",Long.class);
            long nextAnomalies=jdbc.queryForObject("SELECT COUNT(*) FROM provider_payment_anomaly WHERE status='OPEN'",Long.class);
            dead.set(nextDead);anomalies.set(nextAnomalies);
        } catch(org.springframework.dao.DataAccessException unavailable) {failures.increment();}
    }
}
