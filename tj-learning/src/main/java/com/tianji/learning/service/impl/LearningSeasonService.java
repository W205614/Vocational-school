package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.api.client.exam.ExamClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.exam.ExamSettlementPage;
import com.tianji.common.autoconfigure.reliability.OutboxStore;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.UserContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.extern.slf4j.Slf4j;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Awards share a month fence; freezing owns it exclusively. Frozen rankings are immutable. */
@Slf4j
@Service
public class LearningSeasonService {
    public static final DateTimeFormatter MONTH=DateTimeFormatter.ofPattern("yyyyMM");
    private final JdbcTemplate jdbc;private final Clock clock;private final ExamClient exams;
    private final OutboxStore outbox;private final UserClient users;private final TransactionTemplate tx;
    private final TransactionTemplate withoutTransaction;
    public LearningSeasonService(JdbcTemplate jdbc,@Qualifier("learningClock") Clock clock,ExamClient exams,OutboxStore outbox,UserClient users,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.clock=clock;this.exams=exams;this.outbox=outbox;this.users=users;this.tx=new TransactionTemplate(manager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.withoutTransaction=new TransactionTemplate(manager);
        this.withoutTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }
    public static YearMonth parseMonth(String value) {
        try {if(value==null || !value.matches("[0-9]{6}"))throw new IllegalArgumentException();return YearMonth.parse(value,MONTH);}
        catch(RuntimeException invalid){throw new BadRequestException("赛季月份无效，格式为yyyyMM");}
    }
    public String currentMonth(){return YearMonth.now(clock).format(MONTH);}
    public static void ensureSeason(JdbcTemplate jdbc,YearMonth month) {
        // Existing months need no write lock. The subsequent FOR SHARE/FOR UPDATE is
        // a current read, so a concurrent freeze cannot be hidden by an RR snapshot.
        if(!jdbc.queryForList("SELECT board_month FROM learning_season WHERE board_month=?",String.class,month.format(MONTH)).isEmpty())return;
        var boundary=jdbc.queryForList("SELECT start_month FROM learning_rollout_boundary WHERE id=1",String.class);
        if(!boundary.isEmpty() && month.format(MONTH).compareTo(boundary.getFirst())<0) {
            jdbc.update("INSERT INTO learning_season(board_month,name,state,freeze_at,settled_at) VALUES(?,?,'LEGACY_READONLY',?,CURRENT_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE board_month=VALUES(board_month)",month.format(MONTH),month.getYear()+"年"+month.getMonthValue()+"月赛季",month.plusMonths(1).atDay(2).atStartOfDay());return;
        }
        // Only first creation uses an exclusive upsert; avoid a duplicate-key lock upgrade.
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
        long user=UserContext.requireUser();
        String table=frozen?"learning_season_rank":"points_projection";
        var mine=jdbc.queryForList("SELECT * FROM "+table+" WHERE board_month=? AND user_id=? AND points>0",month,user);
        long rank=0;int gap=0;
        if(!mine.isEmpty()) {
            var me=mine.getFirst();
            if(frozen) {
                rank=((Number)me.get("rank_no")).longValue();
                if(rank>1) {
                    var previous=jdbc.queryForList("SELECT points FROM learning_season_rank WHERE board_month=? AND rank_no=?",month,rank-1);
                    if(!previous.isEmpty())gap=Math.max(0,((Number)previous.getFirst().get("points")).intValue()-((Number)me.get("points")).intValue());
                }
            } else {
                var ahead=new ArrayList<Object>();ahead.add(month);
                int points=((Number)me.get("points")).intValue();
                ahead.add(points);ahead.add(points);
                String tie;
                if(me.get("reached_at")==null) {
                    tie="reached_at IS NULL AND user_id<?";ahead.add(user);
                } else {
                    tie="reached_at IS NULL OR reached_at<? OR (reached_at=? AND user_id<?)";
                    ahead.add(me.get("reached_at"));ahead.add(me.get("reached_at"));ahead.add(user);
                }
                String condition=" WHERE board_month=? AND (points>? OR (points=? AND ("+tie+")))";
                rank=1+jdbc.queryForObject("SELECT COUNT(*) FROM points_projection"+condition,Long.class,ahead.toArray());
                if(rank>1) {
                    var previous=jdbc.queryForList("SELECT points FROM points_projection"+condition+" ORDER BY points ASC,reached_at DESC,user_id DESC LIMIT 1",ahead.toArray());
                    if(!previous.isEmpty())gap=Math.max(0,((Number)previous.getFirst().get("points")).intValue()-points);
                }
            }
        }
        String select=frozen?"SELECT rank_no AS `rank`,user_id AS userId,points,reached_at AS reachedAt,display_name AS name FROM learning_season_rank WHERE board_month=? ORDER BY rank_no":
                "SELECT user_id AS userId,points,reached_at AS reachedAt,NULL AS name FROM points_projection WHERE board_month=? AND points>0 ORDER BY points DESC,reached_at ASC,user_id ASC";
        var rows=jdbc.queryForList(select+" LIMIT ? OFFSET ?",month,page.getPageSize(),page.from());
        if(!frozen)for(int index=0;index<rows.size();index++)rows.get(index).put("rank",(long)page.from()+index+1);
        var names=new HashMap<Long,String>();
        if(!frozen && !rows.isEmpty()) {
            var details=users.queryUserByIds(rows.stream().map(r->((Number)r.get("userId")).longValue()).toList());
            if(details!=null)details.forEach(u->names.put(u.getId(),u.getName()));
        }
        for(var row:rows)if(row.get("name")==null)row.put("name",names.getOrDefault(((Number)row.get("userId")).longValue(),"同学"));
        var result=new LinkedHashMap<String,Object>();result.put("month",month);result.put("status",seasons.getFirst().get("state"));result.put("settledAt",seasons.getFirst().get("settled_at"));
        result.put("myRank",rank);result.put("myPoints",mine.isEmpty()?0:mine.getFirst().get("points"));
        result.put("gapToPrevious",gap);
        result.put("total",jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE board_month=? AND points>0",Long.class,month));result.put("list",rows);
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
        // Scan all remote pages before local locking, with a fixed upper bound. Events
        // committed after that bound are reconciled if they arrive after the freeze.
        boolean remoteReady=Boolean.TRUE.equals(withoutTransaction.execute(status->examReceiptsReady(month)));
        tx.executeWithoutResult(status->{
            ensureSeason(jdbc,season);var state=jdbc.queryForMap("SELECT state FROM learning_season WHERE board_month=? FOR UPDATE",month);
            if(Set.of("SETTLED","LEGACY_READONLY").contains(state.get("state")))return;
            // Sign operations retain the request's date, and their outbox key retains that same date even when processing crosses midnight.
            int pending=jdbc.queryForObject("SELECT COUNT(*) FROM reliability_operation WHERE status='PENDING' AND ((kind='SIGN_IN' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.date'))>=? AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.date'))<?) OR (kind='LEARNING_ACCEPT' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.action'))='ACCEPT' AND LEFT(JSON_UNQUOTE(JSON_EXTRACT(payload,'$.occurredAt')),10)>=? AND LEFT(JSON_UNQUOTE(JSON_EXTRACT(payload,'$.occurredAt')),10)<?))",Integer.class,begin.toLocalDate(),end.toLocalDate(),begin.toLocalDate(),end.toLocalDate());
            pending+=jdbc.queryForObject("SELECT COUNT(*) FROM reliability_outbox o LEFT JOIN reliability_inbox i ON i.event_id=o.event_id AND i.consumer_name='points.2' WHERE o.routing_key='sign.in' AND i.event_id IS NULL AND ((RIGHT(o.business_key,10)>=? AND RIGHT(o.business_key,10)<?) OR (o.business_key NOT REGEXP '^sign:[0-9]+:[0-9]{4}-[0-9]{2}-[0-9]{2}$' AND o.created_at>=? AND o.created_at<?))",Integer.class,begin.toLocalDate(),end.toLocalDate(),begin,end);
            if(!remoteReady || pending>0){jdbc.update("UPDATE learning_season SET state='BLOCKED',blocked_reason=? WHERE board_month=?","存在未处理的学习积分或考试事件",month);return;}
            jdbc.update("UPDATE learning_season SET state='SETTLING',blocked_reason=NULL WHERE board_month=?",month);
            jdbc.update("INSERT INTO learning_season_rank(board_month,user_id,rank_no,points,reached_at,display_name) SELECT board_month,user_id,ROW_NUMBER() OVER(ORDER BY points DESC,reached_at ASC,user_id ASC),points,reached_at,NULL FROM points_projection WHERE board_month=? AND points>0",month);
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
    private boolean examReceiptsReady(String month) {
        long cursor=0;Long upperBound=null;
        do {
            ExamSettlementPage page=exams.settlementReadiness(month,cursor,upperBound);
            if(page==null || !month.equals(page.month()) || page.cursor()!=cursor || page.upperBound()<cursor ||
                    upperBound!=null && page.upperBound()!=upperBound || page.pending()<0 || page.eventIds()==null ||
                    page.eventIds().size()>ExamSettlementPage.PAGE_SIZE || new HashSet<>(page.eventIds()).size()!=page.eventIds().size() ||
                    page.eventIds().stream().anyMatch(id->id==null || id.isBlank()))return false;
            if(page.pending()>0)return false;
            var ids=page.eventIds();
            if(!ids.isEmpty()) {
                String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
                long received=jdbc.queryForObject("SELECT COUNT(*) FROM reliability_inbox WHERE consumer_name='learning.exam' AND event_id IN ("+placeholders+")",Long.class,ids.toArray());
                if(received!=ids.size())return false;
            }
            if(page.nextCursor()==null)return true;
            if(ids.size()!=ExamSettlementPage.PAGE_SIZE || page.nextCursor()<=cursor || page.nextCursor()>page.upperBound())return false;
            upperBound=page.upperBound();cursor=page.nextCursor();
        } while(true);
    }
    /** Names enrich a committed snapshot, in bounded batches, without holding the season lock. */
    @Scheduled(fixedDelayString="${tj.learning.season-name-interval-ms:5000}")
    public void hydrateNames() {
        var pending=jdbc.queryForList("SELECT board_month,user_id FROM learning_season_rank WHERE name_resolved_at IS NULL AND name_next_attempt_at<=CURRENT_TIMESTAMP(3) ORDER BY name_next_attempt_at,board_month,user_id LIMIT 100");
        if(pending.isEmpty())return;
        // Reserve the retry window before IO; concurrent workers may overlap, but
        // name_resolved_at's CAS guarantees that the first successful name wins.
        for(var row:pending)jdbc.update("UPDATE learning_season_rank SET name_next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 1 MINUTE) WHERE board_month=? AND user_id=? AND name_resolved_at IS NULL",row.get("board_month"),row.get("user_id"));
        try {
            var ids=pending.stream().map(row->((Number)row.get("user_id")).longValue()).distinct().toList();
            var details=withoutTransaction.execute(status->users.queryUserByIds(ids));
            if(details==null)return;
            var names=new HashMap<Long,String>();
            for(var detail:details)if(ids.contains(detail.getId()) && detail.getName()!=null && !detail.getName().isBlank())names.put(detail.getId(),detail.getName());
            for(var row:pending) {
                String name=names.get(((Number)row.get("user_id")).longValue());
                if(name!=null)jdbc.update("UPDATE learning_season_rank SET display_name=?,name_resolved_at=CURRENT_TIMESTAMP(3) WHERE board_month=? AND user_id=? AND name_resolved_at IS NULL",name,row.get("board_month"),row.get("user_id"));
            }
        } catch(RuntimeException unavailable) {
            // Retry metadata is durable. A nickname dependency never rolls back a settled season.
            log.warn("赛季昵称快照等待重试: {}",unavailable.getClass().getSimpleName());
        }
    }
    public void publishReward(String request,long user,String month,long rule,long coupon,int attempt) {
        outbox.enqueue("season-reward:"+request+":"+attempt,"learning.topic","learning.reward.requested",new RewardRequested(request,user,coupon,month,rule));
    }
    public record RewardRequested(String requestId,long userId,long couponId,String seasonMonth,long ruleId) {}
}
