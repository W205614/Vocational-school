package com.tianji.learning.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/** Scrapes read the last sample; the database is queried only by the scheduler. */
@Slf4j
@Component
public class LearningSeasonMetrics {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private volatile double blocked=Double.NaN;

    public LearningSeasonMetrics(JdbcTemplate jdbc,@Qualifier("learningClock") Clock clock,MeterRegistry meters) {
        this.jdbc=jdbc;this.clock=clock;
        meters.gauge("tj.seasons.blocked",this,metrics->metrics.blocked);
    }
    @Scheduled(fixedDelayString="${tj.learning.season-metrics-interval-ms:15000}")
    public void refresh() {
        try {
            String month=YearMonth.now(clock).format(DateTimeFormatter.ofPattern("yyyyMM"));
            Long sample=jdbc.queryForObject("SELECT COUNT(*) FROM learning_season WHERE state='BLOCKED' AND board_month<?",Long.class,month);
            if(sample!=null)blocked=sample;
        } catch(RuntimeException unavailable) {
            log.warn("赛季阻塞指标采样失败: {}",unavailable.getClass().getSimpleName());
        }
    }
}
