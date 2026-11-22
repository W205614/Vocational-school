package com.zhixuetang.exam.controller;

import com.zhixuetang.api.dto.exam.ExamLearningOverview;
import com.zhixuetang.api.dto.exam.ExamLearningOverview.ActiveAttempt;
import com.zhixuetang.api.dto.exam.ExamLearningOverview.PendingReview;
import com.zhixuetang.common.utils.InternalAuth;
import com.zhixuetang.exam.service.impl.ExamReviewService;
import com.zhixuetang.api.dto.exam.ExamSettlementPage;
import com.zhixuetang.common.exceptions.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/exams")
public class InternalExamController {
    private final JdbcTemplate jdbc;

    /** Only trusted learning service callers supply courses resolved from current entitlement facts.
     * The actor comes from the authenticated relay, never from request parameters. */
    @PostMapping("/learning-overview")
    public ExamLearningOverview learningOverview(@RequestBody List<Long> courseIds) {
        InternalAuth.requireService();
        long user = com.zhixuetang.common.utils.UserContext.requireUser();
        if (courseIds == null
                || courseIds.isEmpty()
                || courseIds.size() > 100
                || courseIds.stream().anyMatch(id -> id == null || id < 1)
                || new HashSet<>(courseIds).size() != courseIds.size())
            throw new BadRequestException("课程参数无效");
        var args = new ArrayList<Object>();
        args.add(user);
        args.add(user);
        args.addAll(courseIds);
        String placeholders =
                String.join(",", Collections.nCopies(courseIds.size(), "?"));
        // Every paper row is an immutable published snapshot. A newer publication supersedes
        // its section's older version; attempts are scoped to the relayed student.
        String sql =
                "SELECT COALESCE(SUM(NOT passed AND NOT waiting),0) AS pending,COALESCE(SUM(NOT passed AND waiting),0) AS waiting FROM (SELECT EXISTS(SELECT 1 FROM exam_attempt a WHERE a.paper_id=p.id AND a.user_id=? AND a.status='FINISHED' AND a.passed=1) AS passed,EXISTS(SELECT 1 FROM exam_attempt a WHERE a.paper_id=p.id AND a.user_id=? AND a.status='WAIT_GRADING') AS waiting FROM exam_paper p WHERE p.course_id IN ("
                        + placeholders
                        + ") AND NOT EXISTS(SELECT 1 FROM exam_paper newer WHERE newer.course_id=p.course_id AND newer.section_id=p.section_id AND newer.version>p.version)) papers";
        var counts = jdbc.queryForMap(sql, args.toArray());
        List<Object> owned = new ArrayList<>();
        owned.add(user);
        owned.addAll(courseIds);
        var active =
                jdbc.query(
                        "SELECT a.id,p.course_id,p.section_id,COALESCE(d.updated_at,a.created_at) AS updated_at"
                                + " FROM exam_attempt a JOIN exam_paper p ON p.id=a.paper_id LEFT JOIN exam_draft d ON d.attempt_id=a.id"
                                + " WHERE a.user_id=? AND a.status='IN_PROGRESS' AND p.course_id IN ("
                                + placeholders
                                + ") ORDER BY updated_at DESC,a.id DESC LIMIT 1",
                        (rs, index) ->
                                new ActiveAttempt(
                                        rs.getLong("id"),
                                        rs.getLong("course_id"),
                                        rs.getLong("section_id"),
                                        rs.getObject("updated_at", LocalDateTime.class)),
                        owned.toArray());
        var review =
                jdbc.query(
                        ExamReviewService.pendingReviewSql(placeholders),
                        (rs, index) ->
                                new PendingReview(
                                        rs.getLong("attempt_id"),
                                        rs.getLong("question_id"),
                                        rs.getLong("course_id"),
                                        rs.getLong("section_id"),
                                        rs.getLong("failed_attempts"),
                                        rs.getObject("last_reviewed_at", LocalDateTime.class)),
                        owned.toArray());
        return new ExamLearningOverview(
                Math.toIntExact(((Number) counts.get("pending")).longValue()),
                Math.toIntExact(((Number) counts.get("waiting")).longValue()),
                active.isEmpty() ? null : active.getFirst(),
                review.isEmpty() ? null : review.getFirst());
    }

    @GetMapping("/published-sections")
    public List<Long> publishedSections(@RequestParam long courseId) {
        InternalAuth.requireService();
        if (courseId <= 0) throw new BadRequestException("课程参数无效");
        return jdbc.queryForList(
                "SELECT DISTINCT section_id FROM exam_paper WHERE course_id=? ORDER BY section_id",
                Long.class,
                courseId);
    }

    @GetMapping("/settlement-readiness")
    public ExamSettlementPage readiness(
            @RequestParam String month,
            @RequestParam(defaultValue = "0") long cursor,
            @RequestParam(required = false) Long upperBound) {
        InternalAuth.requireService();
        YearMonth period;
        try {
            if (month == null || !month.matches("[0-9]{6}")) throw new IllegalArgumentException();
            period = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyyMM"));
        } catch (RuntimeException invalid) {
            throw new BadRequestException("赛季月份无效");
        }
        if (cursor < 0
                || upperBound != null && (upperBound < 0 || cursor > upperBound)
                || upperBound == null && cursor != 0) throw new BadRequestException("结算扫描游标无效");
        long bound =
                upperBound == null
                        ? jdbc.queryForObject(
                                "SELECT COALESCE(MAX(settlement_sequence),0) FROM reliability_outbox",
                                Long.class)
                        : upperBound;
        // An immutable sequence bounds work across pages; publishing/status changes never move an
        // event.
        // The grading business time preserves legacy envelopes and the month's midnight boundary.
        String from =
                " FROM reliability_outbox o LEFT JOIN exam_attempt a ON a.id=CASE WHEN o.business_key REGEXP '^exam:[0-9]+:passed$' THEN CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(o.business_key,':',2),':',-1) AS UNSIGNED) END AND o.business_key=CONCAT('exam:',a.id,':passed') WHERE o.routing_key='exam.passed' AND o.settlement_sequence>? AND o.settlement_sequence<=? AND COALESCE(a.graded_at,o.created_at)>=? AND COALESCE(a.graded_at,o.created_at)<?";
        var rows =
                jdbc.queryForList(
                        "SELECT o.settlement_sequence,o.event_id,o.status"
                                + from
                                + " ORDER BY o.settlement_sequence LIMIT ?",
                        cursor,
                        bound,
                        period.atDay(1).atStartOfDay(),
                        period.plusMonths(1).atDay(1).atStartOfDay(),
                        ExamSettlementPage.PAGE_SIZE + 1);
        boolean more = rows.size() > ExamSettlementPage.PAGE_SIZE;
        var page = more ? rows.subList(0, ExamSettlementPage.PAGE_SIZE) : rows;
        var eventIds = page.stream().map(row -> row.get("event_id").toString()).toList();
        long pending = page.stream().filter(row -> !"SENT".equals(row.get("status"))).count();
        Long next = more ? ((Number) page.getLast().get("settlement_sequence")).longValue() : null;
        return new ExamSettlementPage(month, bound, cursor, eventIds, pending, next);
    }
}
