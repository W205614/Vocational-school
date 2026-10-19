package com.tianji.learning.reliability;

import com.tianji.learning.config.LearningSeasonMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LearningSeasonMetricsTest {
    @Test void scrapeUsesCachedPastMonthCountAndFailedSamplingKeepsLastValue() {
        var jdbc=mock(JdbcTemplate.class);
        var clock=Clock.fixed(Instant.parse("2026-09-30T17:00:00Z"),ZoneId.of("Asia/Shanghai"));
        var meters=new SimpleMeterRegistry();
        try {
            var metrics=new LearningSeasonMetrics(jdbc,clock,meters);var gauge=meters.get("tj.seasons.blocked").gauge();
            assertTrue(Double.isNaN(gauge.value()));verifyNoInteractions(jdbc);
            when(jdbc.queryForObject(anyString(),eq(Long.class),eq("202610"))).thenReturn(3L);
            metrics.refresh();assertEquals(3,gauge.value());assertEquals(3,gauge.value());
            verify(jdbc,times(1)).queryForObject(anyString(),eq(Long.class),eq("202610"));
            when(jdbc.queryForObject(anyString(),eq(Long.class),eq("202610"))).thenThrow(new IllegalStateException("database unavailable"));
            assertDoesNotThrow(metrics::refresh);assertEquals(3,gauge.value());
        } finally {meters.close();}
    }
}
