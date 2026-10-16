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
    /** Only trusted learning service callers supply courses resolved from current entitlement facts.
     * The actor comes from the authenticated relay, never from request parameters. */
    @PostMapping("/learning-overview") public Map<String,Integer> learningOverview(@RequestBody java.util.List<Long> courseIds) {
        InternalAuth.requireService();long user=com.tianji.common.utils.UserContext.requireUser();
        if(courseIds==null || courseIds.isEmpty() || courseIds.size()>100 || courseIds.stream().anyMatch(id->id==null || id<1) || new java.util.HashSet<>(courseIds).size()!=courseIds.size())
            throw new com.tianji.common.exceptions.BadRequestException("课程参数无效");
        var args=new java.util.ArrayList<Object>();args.add(user);args.add(user);args.addAll(courseIds);
        String placeholders=String.join(",",java.util.Collections.nCopies(courseIds.size(),"?"));
        // Every paper row is an immutable published snapshot. A newer publication supersedes
        // its section's older version; attempts are scoped to the relayed student.
        String sql="SELECT COALESCE(SUM(NOT passed AND NOT waiting),0) AS pending,COALESCE(SUM(NOT passed AND waiting),0) AS waiting FROM (SELECT EXISTS(SELECT 1 FROM exam_attempt a WHERE a.paper_id=p.id AND a.user_id=? AND a.status='FINISHED' AND a.passed=1) AS passed,EXISTS(SELECT 1 FROM exam_attempt a WHERE a.paper_id=p.id AND a.user_id=? AND a.status='WAIT_GRADING') AS waiting FROM exam_paper p WHERE p.course_id IN ("+placeholders+") AND NOT EXISTS(SELECT 1 FROM exam_paper newer WHERE newer.course_id=p.course_id AND newer.section_id=p.section_id AND newer.version>p.version)) papers";
        var counts=jdbc.queryForMap(sql,args.toArray());
        return Map.of("pendingExams",Math.toIntExact(((Number)counts.get("pending")).longValue()),"waitingForGradingExams",Math.toIntExact(((Number)counts.get("waiting")).longValue()));
    }
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
