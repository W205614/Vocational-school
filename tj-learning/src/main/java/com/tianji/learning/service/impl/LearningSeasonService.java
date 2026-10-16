package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.api.client.exam.ExamClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.common.autoconfigure.reliability.OutboxStore;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.UserContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** The season row serializes awarding and settlement; settled rankings are immutable. */
@Service
public class LearningSeasonService {
    public static final DateTimeFormatter MONTH=DateTimeFormatter.ofPattern("yyyyMM");
    private final JdbcTemplate jdbc;private final Clock clock;private final ExamClient exams;
    private final OutboxStore outbox;private final UserClient users;private final TransactionTemplate tx;
    public LearningSeasonService(JdbcTemplate jdbc,@Qualifier("learningClock") Clock clock,ExamClient exams,OutboxStore outbox,UserClient users,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.clock=clock;this.exams=exams;this.outbox=outbox;this.users=users;this.tx=new TransactionTemplate(manager);
    }
    public static YearMonth parseMonth(String value) {
        try {if(value==null || !value.matches("[0-9]{6}"))throw new IllegalArgumentException();return YearMonth.parse(value,MONTH);}
        catch(RuntimeException invalid){throw new BadRequestException("赛季月份无效，格式为yyyyMM");}
    }
    public String currentMonth(){return YearMonth.now(clock).format(MONTH);}
    public static void ensureSeason(JdbcTemplate jdbc,YearMonth month) {
        var boundary=jdbc.queryForList("SELECT start_month FROM learning_rollout_boundary WHERE id=1",String.class);
        if(!boundary.isEmpty() && month.format(MONTH).compareTo(boundary.getFirst())<0) {
            jdbc.update("INSERT INTO learning_season(board_month,name,state,freeze_at,settled_at) VALUES(?,?,'LEGACY_READONLY',?,CURRENT_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE board_month=VALUES(board_month)",month.format(MONTH),month.getYear()+"年"+month.getMonthValue()+"月赛季",month.plusMonths(1).atDay(2).atStartOfDay());return;
        }
        // An upsert acquires an exclusive record lock directly. INSERT IGNORE takes
        // shared duplicate-key locks that deadlock when concurrent awards upgrade
        // them to the season's FOR UPDATE lock.
        jdbc.update("INSERT INTO learning_season(board_month,name,state,freeze_at) VALUES(?,?,'OPEN',?) ON DUPLICATE KEY UPDATE board_month=VALUES(board_month)",month.format(MONTH),month.getYear()+"年"+month.getMonthValue()+"月赛季",month.plusMonths(1).atDay(2).atStartOfDay());
    }
    public List<Map<String,Object>> seasons() {
        ensureSeason(jdbc,YearMonth.now(clock));ensureSeason(jdbc,YearMonth.now(clock).plusMonths(1));
        return jdbc.queryForList("SELECT board_month AS month,name,state AS status,freeze_at AS freezeAt,settled_at AS settledAt,blocked_reason AS blockedReason FROM learning_season ORDER BY board_month DESC");
    }
    public Map<String,Object> board(String month,PageQuery page) {
        page.validate();if(month==null)month=currentMonth();parseMonth(month);
        if(month.equals(currentMonth()) || month.equals(YearMonth.now(clock).plusMonths(1).format(MONTH)))ensureSeason(jdbc,parseMonth(month));
        var seasons=jdbc.queryForList("SELECT state,settled_at FROM learning_season WHERE board_month=?",month);
        if(seasons.isEmpty())throw new BadRequestException("赛季不存在");
        boolean frozen=Set.of("SETTLED","LEGACY_READONLY").contains(seasons.getFirst().get("state"));
        String ranked=frozen?"SELECT user_id,points,reached_at,rank_no,display_name FROM learning_season_rank WHERE board_month=?":
            "SELECT user_id,points,reached_at,ROW_NUMBER() OVER(ORDER BY points DESC,reached_at ASC,user_id ASC) rank_no,NULL display_name FROM points_projection WHERE board_month=? AND points>0";
        long user=UserContext.requireUser();var mine=jdbc.queryForList("SELECT * FROM ("+ranked+") ranked WHERE user_id=?",month,user);
        var rows=jdbc.queryForList("SELECT rank_no AS `rank`,user_id AS userId,points,reached_at AS reachedAt,display_name AS name FROM ("+ranked+") ranked ORDER BY rank_no LIMIT ? OFFSET ?",month,page.getPageSize(),page.from());
        var names=new HashMap<Long,String>();
        if(!frozen && !rows.isEmpty()) {
            var details=users.queryUserByIds(rows.stream().map(r->((Number)r.get("userId")).longValue()).toList());
            if(details!=null)details.forEach(u->names.put(u.getId(),u.getName()));
        }
        for(var row:rows)if(row.get("name")==null)row.put("name",names.getOrDefault(((Number)row.get("userId")).longValue(),"同学"));
        var result=new LinkedHashMap<String,Object>();result.put("month",month);result.put("status",seasons.getFirst().get("state"));result.put("settledAt",seasons.getFirst().get("settled_at"));
        result.put("myRank",mine.isEmpty()?0:mine.getFirst().get("rank_no"));result.put("myPoints",mine.isEmpty()?0:mine.getFirst().get("points"));
        int gap=0;if(!mine.isEmpty() && ((Number)mine.getFirst().get("rank_no")).intValue()>1) {
            var previous=jdbc.queryForList("SELECT points FROM ("+ranked+") ranked WHERE rank_no=?",month,((Number)mine.getFirst().get("rank_no")).longValue()-1);
            if(!previous.isEmpty())gap=Math.max(0,((Number)previous.getFirst().get("points")).intValue()-((Number)mine.getFirst().get("points")).intValue());
        }result.put("gapToPrevious",gap);
        result.put("total",jdbc.queryForObject("SELECT COUNT(*) FROM ("+ranked+") ranked",Long.class,month));result.put("list",rows);
        result.put("rewardRules",jdbc.queryForList("SELECT rank_from AS rankFrom,rank_to AS rankTo,coupon_id AS couponId FROM learning_reward_rule WHERE board_month=? AND enabled=1 ORDER BY rank_from",month));return result;
    }
    @Scheduled(fixedDelayString="${tj.learning.season-interval-ms:60000}")
    public void maintain() {
        // Empty months have no projection row to discover after a long outage.
        // Start at the immutable rollout boundary; never invent older seasons.
        YearMonth current=YearMonth.now(clock),through=current.plusMonths(1);
        var boundary=jdbc.queryForList("SELECT start_month FROM learning_rollout_boundary WHERE id=1",String.class);
        YearMonth first=boundary.isEmpty()?current:parseMonth(boundary.getFirst());
        for(YearMonth month=first;!month.isAfter(through);month=month.plusMonths(1))ensureSeason(jdbc,month);
        for(String month:jdbc.queryForList("SELECT DISTINCT board_month FROM points_projection",String.class))ensureSeason(jdbc,parseMonth(month));
        for(String month:jdbc.queryForList("SELECT board_month FROM learning_season WHERE state NOT IN ('SETTLED','LEGACY_READONLY') AND freeze_at<=? ORDER BY board_month",String.class,LocalDateTime.now(clock))) {
            try{settle(month);}catch(Exception error){jdbc.update("UPDATE learning_season SET state='BLOCKED',blocked_reason=? WHERE board_month=? AND state<>'SETTLED'","结算依赖不可用，等待下次检查",month);}
        }
    }
    public void settle(String month) {
        YearMonth season=parseMonth(month);LocalDateTime begin=season.atDay(1).atStartOfDay(),end=season.plusMonths(1).atDay(1).atStartOfDay();
        if(LocalDateTime.now(clock).isBefore(season.plusMonths(1).atDay(2).atStartOfDay()))throw new BadRequestException("赛季仍处于结算宽限期");
        // Remote producer readiness must be checked before local locking. A late event after freeze is reconciled.
        var examReadiness=exams.settlementReadiness(end.toString());
        boolean remoteReady=Boolean.TRUE.equals(examReadiness.get("ready"));
        tx.executeWithoutResult(status->{
            ensureSeason(jdbc,season);var state=jdbc.queryForMap("SELECT state FROM learning_season WHERE board_month=? FOR UPDATE",month);
            if(Set.of("SETTLED","LEGACY_READONLY").contains(state.get("state")))return;
            // Sign operations retain the request's date, and their outbox key retains that same date even when processing crosses midnight.
            int pending=jdbc.queryForObject("SELECT COUNT(*) FROM reliability_operation WHERE status='PENDING' AND ((kind='SIGN_IN' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.date'))>=? AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.date'))<?) OR (kind='LEARNING_ACCEPT' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.action'))='ACCEPT' AND LEFT(JSON_UNQUOTE(JSON_EXTRACT(payload,'$.occurredAt')),10)>=? AND LEFT(JSON_UNQUOTE(JSON_EXTRACT(payload,'$.occurredAt')),10)<?))",Integer.class,begin.toLocalDate(),end.toLocalDate(),begin.toLocalDate(),end.toLocalDate());
            pending+=jdbc.queryForObject("SELECT COUNT(*) FROM reliability_outbox o LEFT JOIN reliability_inbox i ON i.event_id=o.event_id AND i.consumer_name='points.2' WHERE o.routing_key='sign.in' AND i.event_id IS NULL AND ((RIGHT(o.business_key,10)>=? AND RIGHT(o.business_key,10)<?) OR (o.business_key NOT REGEXP '^sign:[0-9]+:[0-9]{4}-[0-9]{2}-[0-9]{2}$' AND o.created_at>=? AND o.created_at<?))",Integer.class,begin.toLocalDate(),end.toLocalDate(),begin,end);
            Object eventIds=examReadiness.get("eventIds");
            if(eventIds instanceof List<?> ids) {
                for(int start=0;start<ids.size();start+=500){var chunk=ids.subList(start,Math.min(ids.size(),start+500));String placeholders=String.join(",",Collections.nCopies(chunk.size(),"?"));int received=jdbc.queryForObject("SELECT COUNT(*) FROM reliability_inbox WHERE consumer_name='learning.exam' AND event_id IN ("+placeholders+")",Integer.class,chunk.toArray());pending+=chunk.size()-received;}
            }else if(!examReadiness.containsKey("eventIds")){pending++;}
            if(!remoteReady || pending>0){jdbc.update("UPDATE learning_season SET state='BLOCKED',blocked_reason=? WHERE board_month=?","存在未处理的学习积分或考试事件",month);return;}
            jdbc.update("UPDATE learning_season SET state='SETTLING',blocked_reason=NULL WHERE board_month=?",month);
            jdbc.update("INSERT INTO learning_season_rank(board_month,user_id,rank_no,points,reached_at,display_name) SELECT board_month,user_id,ROW_NUMBER() OVER(ORDER BY points DESC,reached_at ASC,user_id ASC),points,reached_at,'同学' FROM points_projection WHERE board_month=? AND points>0",month);
            var identities=jdbc.queryForList("SELECT user_id FROM learning_season_rank WHERE board_month=? ORDER BY rank_no",Long.class,month);
            for(int start=0;start<identities.size();start+=100){var details=users.queryUserByIds(identities.subList(start,Math.min(start+100,identities.size())));if(details!=null)for(var user:details)if(user.getName()!=null)jdbc.update("UPDATE learning_season_rank SET display_name=? WHERE board_month=? AND user_id=?",user.getName(),month,user.getId());}
            LocalDateTime now=LocalDateTime.now(clock);
            jdbc.update("INSERT IGNORE INTO learning_achievement(user_id,code,season_month,name,awarded_at) SELECT user_id,'SEASON_TOP10',board_month,'月度前十',? FROM learning_season_rank WHERE board_month=? AND rank_no<=10",now,month);
            jdbc.update("INSERT IGNORE INTO learning_achievement(user_id,code,season_month,name,awarded_at) SELECT user_id,'SEASON_TOP3',board_month,'月度前三',? FROM learning_season_rank WHERE board_month=? AND rank_no<=3",now,month);
            var winners=jdbc.queryForList("SELECT r.user_id,r.rank_no,c.id rule_id,c.coupon_id FROM learning_season_rank r JOIN learning_reward_rule c ON c.board_month=r.board_month AND c.enabled=1 AND r.rank_no BETWEEN c.rank_from AND c.rank_to WHERE r.board_month=?",month);
            for(var winner:winners) {
                String request=UUID.randomUUID().toString();long user=((Number)winner.get("user_id")).longValue();
                jdbc.update("INSERT INTO learning_reward_request(request_id,board_month,user_id,rank_no,rule_id,coupon_id) VALUES(?,?,?,?,?,?)",request,month,user,winner.get("rank_no"),winner.get("rule_id"),winner.get("coupon_id"));
                publishReward(request,user,month,((Number)winner.get("rule_id")).longValue(),((Number)winner.get("coupon_id")).longValue(),1);
            }
            jdbc.update("UPDATE learning_season SET state='SETTLED',settled_at=?,blocked_reason=NULL WHERE board_month=?",now,month);
        });
    }
    public void publishReward(String request,long user,String month,long rule,long coupon,int attempt) {
        outbox.enqueue("season-reward:"+request+":"+attempt,"learning.topic","learning.reward.requested",new RewardRequested(request,user,coupon,month,rule));
    }
    public record RewardRequested(String requestId,long userId,long couponId,String seasonMonth,long ruleId) {}
}
