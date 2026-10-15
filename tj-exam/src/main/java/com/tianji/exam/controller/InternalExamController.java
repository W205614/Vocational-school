package com.tianji.exam.controller;

import com.tianji.common.utils.InternalAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;

@RestController @RequiredArgsConstructor @RequestMapping("/internal/v1/exams")
public class InternalExamController {
    private final JdbcTemplate jdbc;
    @GetMapping("/settlement-readiness") public Map<String,Object> readiness(@RequestParam LocalDateTime before) {
        InternalAuth.requireService();
        // Use the committed grading time across midnight, including legacy events
        // whose payload predates the explicit occurredAt field.
        String from=" FROM reliability_outbox o LEFT JOIN exam_attempt a ON o.business_key=CONCAT('exam:',a.id,':passed') WHERE o.routing_key='exam.passed' AND COALESCE(a.graded_at,o.created_at)>=? AND COALESCE(a.graded_at,o.created_at)<?";
        LocalDateTime start=before.minusMonths(1);
        long pending=jdbc.queryForObject("SELECT COUNT(*)"+from+" AND o.status<>'SENT'",Long.class,start,before);
        var eventIds=jdbc.queryForList("SELECT o.event_id"+from+" ORDER BY o.created_at,o.event_id LIMIT 10001",String.class,start,before);
        boolean truncated=eventIds.size()>10000;
        return Map.of("ready",pending==0 && !truncated,"pending",pending,"eventIds",truncated?eventIds.subList(0,10000):eventIds,"truncated",truncated);
    }
}
