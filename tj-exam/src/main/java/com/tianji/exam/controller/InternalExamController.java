package com.tianji.exam.controller;

import com.tianji.common.utils.InternalAuth;
import com.tianji.api.dto.exam.ExamSettlementPage;
import com.tianji.common.exceptions.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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
    @GetMapping("/settlement-readiness") public ExamSettlementPage readiness(
            @RequestParam String month,@RequestParam(defaultValue="0") long cursor,
            @RequestParam(required=false) Long upperBound) {
        InternalAuth.requireService();
        YearMonth period;
        try {
            if(month==null || !month.matches("[0-9]{6}"))throw new IllegalArgumentException();
            period=YearMonth.parse(month,DateTimeFormatter.ofPattern("yyyyMM"));
        } catch(RuntimeException invalid) {throw new BadRequestException("赛季月份无效");}
        if(cursor<0 || upperBound!=null && (upperBound<0 || cursor>upperBound) || upperBound==null && cursor!=0)
            throw new BadRequestException("结算扫描游标无效");
        long bound=upperBound==null?jdbc.queryForObject("SELECT COALESCE(MAX(settlement_sequence),0) FROM reliability_outbox",Long.class):upperBound;
        // An immutable sequence bounds work across pages; publishing/status changes never move an event.
        // The grading business time preserves legacy envelopes and the month's midnight boundary.
        String from=" FROM reliability_outbox o LEFT JOIN exam_attempt a ON a.id=CASE WHEN o.business_key REGEXP '^exam:[0-9]+:passed$' THEN CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(o.business_key,':',2),':',-1) AS UNSIGNED) END AND o.business_key=CONCAT('exam:',a.id,':passed') WHERE o.routing_key='exam.passed' AND o.settlement_sequence>? AND o.settlement_sequence<=? AND COALESCE(a.graded_at,o.created_at)>=? AND COALESCE(a.graded_at,o.created_at)<?";
        var rows=jdbc.queryForList("SELECT o.settlement_sequence,o.event_id,o.status"+from+" ORDER BY o.settlement_sequence LIMIT ?",
                cursor,bound,period.atDay(1).atStartOfDay(),period.plusMonths(1).atDay(1).atStartOfDay(),ExamSettlementPage.PAGE_SIZE+1);
        boolean more=rows.size()>ExamSettlementPage.PAGE_SIZE;
        var page=more?rows.subList(0,ExamSettlementPage.PAGE_SIZE):rows;
        var eventIds=page.stream().map(row->row.get("event_id").toString()).toList();
        long pending=page.stream().filter(row->!"SENT".equals(row.get("status"))).count();
        Long next=more?((Number)page.getLast().get("settlement_sequence")).longValue():null;
        return new ExamSettlementPage(month,bound,cursor,eventIds,pending,next);
    }
}
