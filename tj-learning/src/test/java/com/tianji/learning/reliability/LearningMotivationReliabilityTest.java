package com.tianji.learning.reliability;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.api.client.exam.ExamClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.api.dto.exam.ExamSettlementPage;
import com.tianji.common.autoconfigure.reliability.*;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.service.impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class LearningMotivationReliabilityTest {
    private JdbcTemplate jdbc;private PointsRecordServiceImpl points;private LearningSeasonService seasons;private YearMonth month;private Clock clock;private long user;private InboxStore inbox;private DataSourceTransactionManager manager;
    private ExamClient examClient;private UserClient userClient;
    @BeforeEach void setup() {
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_learning?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
        jdbc=new JdbcTemplate(source);manager=new DataSourceTransactionManager(source);inbox=new InboxStore(jdbc,new TransactionTemplate(manager));user=IdWorker.getId();
        month=YearMonth.of(3000+ThreadLocalRandom.current().nextInt(5000),1+ThreadLocalRandom.current().nextInt(12));
        clock=Clock.fixed(month.plusMonths(1).atDay(3).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),ZoneId.of("Asia/Shanghai"));points=new PointsRecordServiceImpl(jdbc,inbox,clock);
        examClient=mock(ExamClient.class);when(examClient.settlementReadiness(anyString(),anyLong(),nullable(Long.class))).thenAnswer(call->new ExamSettlementPage(call.getArgument(0),0,0,List.of(),0,null));
        userClient=mock(UserClient.class);var detail=new UserDTO();detail.setId(user);detail.setName("真实昵称快照");when(userClient.queryUserByIds(any())).thenReturn(List.of(detail));
        seasons=new LearningSeasonService(jdbc,clock,examClient,new OutboxStore(jdbc,JsonMapper.builder().findAndAddModules().build()),userClient,manager);
        UserContext.setUser(user);
    }
    @AfterEach void cleanup(){UserContext.removeUser();}
    @Test void frozenRankingAndNameStayStableWhileLateAwardIsRetainedForReconciliation() {
        String value=month.format(LearningSeasonService.MONTH);LocalDateTime time=month.atDay(3).atTime(10,0);
        points.award(user,10,PointsRecordType.LEARNING,"first:"+user,time);
        seasons.settle(value);seasons.settle(value);seasons.hydrateNames();
        var before=seasons.board(value,new PageQuery());assertEquals("SETTLED",before.get("status"));assertEquals(1,((Number)before.get("myRank")).intValue());
        assertEquals("真实昵称快照",((Map<?,?>)((List<?>)before.get("list")).getFirst()).get("name"));
        points.award(user,10,PointsRecordType.LEARNING,"late:"+user,time.plusHours(1));
        var after=seasons.board(value,new PageQuery());assertEquals(10,((Number)after.get("myPoints")).intValue());
        assertEquals(20,jdbc.queryForObject("SELECT SUM(points) FROM points_record WHERE user_id=?",Integer.class,user));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_points_reconciliation WHERE user_id=?",Integer.class,user));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM points_record WHERE user_id=? AND late=1",Integer.class,user));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM learning_achievement WHERE user_id=? AND season_month=?",Integer.class,user,value));
    }
    @Test void equalPointsRankByReachingTimeThenNumericUserIdAndGapIsReturned() {
        String value=month.format(LearningSeasonService.MONTH);LocalDateTime time=month.atDay(3).atTime(10,0);
        points.award(user+2,10,PointsRecordType.LEARNING,"first:"+(user+2),time.plusMinutes(1));
        points.award(user+1,10,PointsRecordType.LEARNING,"first:"+(user+1),time);
        points.award(user,10,PointsRecordType.LEARNING,"first:"+user,time);
        var board=seasons.board(value,new PageQuery());var list=(List<?>)board.get("list");
        assertEquals(user,((Number)((Map<?,?>)list.get(0)).get("userId")).longValue());
        assertEquals(user+1,((Number)((Map<?,?>)list.get(1)).get("userId")).longValue());
        assertEquals(user+2,((Number)((Map<?,?>)list.get(2)).get("userId")).longValue());
        assertEquals(0,board.get("gapToPrevious"));
    }
    @Test void concurrentCompletedSectionsShareDailyFiftyPointQuotaAndNextMonthStartsFresh() throws Exception {
        LocalDateTime time=month.atDay(3).atTime(10,0);
        try(var pool=Executors.newFixedThreadPool(12)) {
            List<Future<?>> jobs=new ArrayList<>();for(int i=0;i<30;i++){String key="complete-test:"+user+":"+(i%15);jobs.add(pool.submit(()->points.award(user,10,PointsRecordType.LEARNING,key,time)));}
            for(var job:jobs)job.get(30,TimeUnit.SECONDS);
        }
        assertEquals(50,jdbc.queryForObject("SELECT SUM(points) FROM points_record WHERE user_id=?",Integer.class,user));
        points.award(user,10,PointsRecordType.LEARNING,"next-month:"+user,month.plusMonths(1).atDay(1).atStartOfDay());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM points_projection WHERE user_id=?",Integer.class,user));
        assertEquals(60,jdbc.queryForObject("SELECT SUM(points) FROM points_record WHERE user_id=?",Integer.class,user));
    }
    @Test void publishedButUnconsumedSignEventBlocksSettlementUntilConsumerReceiptExists() {
        String value=month.format(LearningSeasonService.MONTH);LocalDateTime time=month.atDay(3).atTime(10,0);String event=UUID.randomUUID().toString();
        points.award(user,10,PointsRecordType.LEARNING,"first:"+user,time);
        LocalDateTime createdAfterMonth=month.plusMonths(1).atDay(1).atTime(0,1);
        jdbc.update("INSERT INTO reliability_outbox(event_id,business_key,exchange_name,routing_key,event_type,schema_version,payload,status,next_attempt_at,created_at) VALUES(?,?,'learning.topic','sign.in','sign.in',1,'{}','SENT',?,?)",event,"sign:"+user+":"+time.toLocalDate(),createdAfterMonth,createdAfterMonth);
        String unrelated=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO reliability_outbox(event_id,business_key,exchange_name,routing_key,event_type,schema_version,payload,status,next_attempt_at,created_at) VALUES(?,?,'learning.topic','sign.in','sign.in',1,'{}','PENDING',?,?)",unrelated,"sign:"+user+":"+month.minusMonths(1).atDay(3),time,time);
        seasons.settle(value);assertEquals("BLOCKED",jdbc.queryForObject("SELECT state FROM learning_season WHERE board_month=?",String.class,value));
        inbox.once("points.2",event,()->{});seasons.settle(value);
        assertEquals("SETTLED",jdbc.queryForObject("SELECT state FROM learning_season WHERE board_month=?",String.class,value));
    }
    @Test void settlementCreatesOneDurableRewardAndReplayingAcknowledgementCannotDuplicateIt() {
        String value=month.format(LearningSeasonService.MONTH);long rule=IdWorker.getId();
        jdbc.update("INSERT INTO learning_reward_rule(id,board_month,rank_from,rank_to,coupon_id,enabled,created_by) VALUES(?,?,1,3,99,1,?)",rule,value,user);
        points.award(user,10,PointsRecordType.LEARNING,"reward-first:"+user,month.atDay(3).atTime(10,0));
        seasons.settle(value);seasons.settle(value);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_reward_request WHERE user_id=?",Integer.class,user));
        String request=jdbc.queryForObject("SELECT request_id FROM learning_reward_request WHERE user_id=?",String.class,user);
        var listener=new com.tianji.learning.mq.LearningRewardListener(jdbc,inbox);var properties=new org.springframework.amqp.core.MessageProperties();properties.setMessageId(UUID.randomUUID().toString());var raw=new org.springframework.amqp.core.Message(new byte[0],properties);
        listener.fulfilled(new com.tianji.learning.mq.LearningRewardListener.Fulfilled(request,123),raw);listener.fulfilled(new com.tianji.learning.mq.LearningRewardListener.Fulfilled(request,123),raw);
        assertEquals("FULFILLED",jdbc.queryForObject("SELECT status FROM learning_reward_request WHERE request_id=?",String.class,request));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM reliability_outbox WHERE business_key LIKE ?",Integer.class,"season-reward:"+request+":%"));
    }
    @Test void outOfOrderCompletedDaysStillUnlockOneSevenDayAchievement() {
        var service=new LearningMilestoneService(jdbc,points,clock);var tx=new TransactionTemplate(manager);long course=IdWorker.getId();
        for(int offset:new int[]{0,1,2,4,5,6,3,3})tx.executeWithoutResult(status->service.completed(user,course,course+offset+1,false,month.atDay(10+offset).atTime(10,0)));
        assertEquals(7,jdbc.queryForObject("SELECT COUNT(*) FROM learning_milestone WHERE user_id=?",Integer.class,user));
        assertEquals(70,jdbc.queryForObject("SELECT SUM(points) FROM points_record WHERE user_id=?",Integer.class,user));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_achievement WHERE user_id=? AND code='LEARNING_7_DAYS'",Integer.class,user));
    }
    @Test void dailyStatisticsUseBusinessDayInsteadOfDelayedCreditTime() {
        var today=LocalDate.now(clock);
        points.award(user,10,PointsRecordType.LEARNING,"yesterday:"+user,today.minusDays(1).atTime(12,0));
        assertTrue(points.queryMyPointsToday().isEmpty());
        points.award(user,10,PointsRecordType.LEARNING,"today:"+user,today.atStartOfDay());
        var stats=points.queryMyPointsToday();assertEquals(1,stats.size());assertEquals(10,stats.getFirst().getPoints());assertEquals(1,stats.getFirst().getTypeCode());
    }

    @Test void remotePagesAreCheckedBeforeLockingAndMissingReceiptOnSecondPageBlocksFreeze() {
        String value=month.format(LearningSeasonService.MONTH);
        points.award(user,10,PointsRecordType.LEARNING,"page-first:"+user,month.atDay(3).atTime(10,0));
        var ids=java.util.stream.IntStream.range(0,1001).mapToObj(index->UUID.randomUUID().toString()).toList();
        jdbc.batchUpdate("INSERT INTO reliability_inbox(consumer_name,event_id) VALUES('learning.exam',?)",ids.subList(0,1000),1000,(statement,id)->statement.setString(1,id));
        when(examClient.settlementReadiness(eq(value),anyLong(),nullable(Long.class))).thenAnswer(call->{
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(),"Remote IO must precede the freeze transaction");
            long cursor=call.getArgument(1);
            if(cursor==0){assertNull(call.getArgument(2));return new ExamSettlementPage(value,2000,0,ids.subList(0,1000),0,1000L);}
            assertEquals(1000,cursor);assertEquals(2000L,(Long)call.getArgument(2));
            return new ExamSettlementPage(value,2000,1000,ids.subList(1000,1001),0,null);
        });
        seasons.settle(value);
        assertEquals("BLOCKED",jdbc.queryForObject("SELECT state FROM learning_season WHERE board_month=?",String.class,value));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_season_rank WHERE board_month=?",Integer.class,value));
        inbox.once("learning.exam",ids.getLast(),()->{});seasons.settle(value);
        assertEquals("SETTLED",jdbc.queryForObject("SELECT state FROM learning_season WHERE board_month=?",String.class,value));
        verify(examClient,times(4)).settlementReadiness(eq(value),anyLong(),nullable(Long.class));
    }

    @Test void malformedOrPendingRemotePageCannotBeMistakenForACompletedScan() {
        String value=month.format(LearningSeasonService.MONTH);
        points.award(user,10,PointsRecordType.LEARNING,"invalid-page:"+user,month.atDay(3).atTime(10,0));
        for(var page:List.of(new ExamSettlementPage(value,100,0,List.of(),1,null),
                new ExamSettlementPage(value,100,0,List.of(),0,0L),
                new ExamSettlementPage("199901",100,0,List.of(),0,null))) {
            when(examClient.settlementReadiness(value,0,null)).thenReturn(page);seasons.settle(value);
            assertEquals("BLOCKED",jdbc.queryForObject("SELECT state FROM learning_season WHERE board_month=?",String.class,value));
        }
    }

    @Test void differentLearnersShareMonthFenceWhileFreezeWaitsAndLateAwardsReconcile() throws Exception {
        String value=month.format(LearningSeasonService.MONTH);LocalDateTime time=month.atDay(3).atTime(10,0);
        LearningSeasonService.ensureSeason(jdbc,month);
        CountDownLatch sharedHeld=new CountDownLatch(1),release=new CountDownLatch(1),freezeStarted=new CountDownLatch(1);
        when(examClient.settlementReadiness(value,0,null)).thenAnswer(call->{freezeStarted.countDown();return new ExamSettlementPage(value,0,0,List.of(),0,null);});
        try(var pool=Executors.newFixedThreadPool(3)) {
            var first=pool.submit(()->new TransactionTemplate(manager).executeWithoutResult(status->{
                points.award(user,10,PointsRecordType.LEARNING,"shared-1:"+user,time);sharedHeld.countDown();
                try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
            }));
            try {
                assertTrue(sharedHeld.await(5,TimeUnit.SECONDS));
                // This completes while another user's transaction retains its FOR SHARE lock.
                pool.submit(()->points.award(user+1,10,PointsRecordType.LEARNING,"shared-2:"+user,time)).get(5,TimeUnit.SECONDS);
                var freeze=pool.submit(()->seasons.settle(value));assertTrue(freezeStarted.await(5,TimeUnit.SECONDS));
                assertThrows(TimeoutException.class,()->freeze.get(200,TimeUnit.MILLISECONDS));
                release.countDown();first.get(5,TimeUnit.SECONDS);freeze.get(5,TimeUnit.SECONDS);
            } finally {release.countDown();}
        }
        assertEquals("SETTLED",jdbc.queryForObject("SELECT state FROM learning_season WHERE board_month=?",String.class,value));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM learning_season_rank WHERE board_month=? AND user_id IN (?,?)",Integer.class,value,user,user+1));
        points.award(user+2,10,PointsRecordType.LEARNING,"after-freeze:"+user,time.plusHours(1));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_points_reconciliation WHERE board_month=? AND user_id=?",Integer.class,value,user+2));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM points_projection WHERE board_month=? AND user_id=?",Integer.class,value,user+2));
    }

    @Test void nameTimeoutCannotBlockRewardsAndFirstSuccessfulNameRemainsFixed() {
        String value=month.format(LearningSeasonService.MONTH);long rule=IdWorker.getId();
        jdbc.update("INSERT INTO learning_reward_rule(id,board_month,rank_from,rank_to,coupon_id,enabled,created_by) VALUES(?,?,1,1,99,1,?)",rule,value,user);
        points.award(user,10,PointsRecordType.LEARNING,"name-timeout:"+user,month.atDay(3).atTime(10,0));
        when(userClient.queryUserByIds(any())).thenThrow(new org.springframework.web.client.ResourceAccessException("synthetic timeout"));
        seasons.settle(value);verifyNoInteractions(userClient);assertDoesNotThrow(()->seasons.hydrateNames());
        assertEquals("SETTLED",jdbc.queryForObject("SELECT state FROM learning_season WHERE board_month=?",String.class,value));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_reward_request WHERE board_month=? AND user_id=?",Integer.class,value,user));
        assertNull(jdbc.queryForObject("SELECT display_name FROM learning_season_rank WHERE board_month=? AND user_id=?",String.class,value,user));
        jdbc.update("UPDATE learning_season_rank SET name_next_attempt_at=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE board_month=? AND user_id=?",value,user);
        var name=new UserDTO();name.setId(user);name.setName("首次成功昵称");
        doAnswer(call->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());return List.of(name);}).when(userClient).queryUserByIds(any());
        seasons.hydrateNames();assertEquals("首次成功昵称",jdbc.queryForObject("SELECT display_name FROM learning_season_rank WHERE board_month=? AND user_id=?",String.class,value,user));
        name.setName("之后的新昵称");seasons.hydrateNames();
        assertEquals("首次成功昵称",jdbc.queryForObject("SELECT display_name FROM learning_season_rank WHERE board_month=? AND user_id=?",String.class,value,user));
    }

    /** Real JDBC and production maintain; producer/name services are contract mocks, not broker proof. */
    private LearningSeasonService maintenanceAt(LocalDateTime now,String nickname) {
        var fixed=Clock.fixed(now.atZone(ZoneId.of("Asia/Shanghai")).toInstant(),ZoneId.of("Asia/Shanghai"));
        var exams=mock(ExamClient.class);when(exams.settlementReadiness(anyString(),anyLong(),nullable(Long.class))).thenAnswer(call->new ExamSettlementPage(call.getArgument(0),0,0,List.of(),0,null));
        var names=mock(UserClient.class);when(names.queryUserByIds(any())).thenAnswer(call->{
            var requested=(Iterable<Long>)call.getArgument(0);boolean present=false;for(Long id:requested)if(id==user)present=true;
            if(!present)return List.of();var detail=new UserDTO();detail.setId(user);detail.setName(nickname);return List.of(detail);
        });
        return new LearningSeasonService(jdbc,fixed,exams,new OutboxStore(jdbc,JsonMapper.builder().findAndAddModules().build()),names,manager);
    }
    private YearMonth unusedLeapRecoveryPeriod() {
        var boundary=LearningSeasonService.parseMonth(jdbc.queryForObject("SELECT start_month FROM learning_rollout_boundary WHERE id=1",String.class));
        int year=boundary.getYear()+2;while(!Year.isLeap(year))year++;
        for(int attempt=0;attempt<16;attempt++,year+=4) {
            YearMonth start=YearMonth.of(year-1,11),end=start.plusMonths(4);String first=start.format(LearningSeasonService.MONTH),last=end.format(LearningSeasonService.MONTH);
            int occupied=jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM learning_season WHERE board_month BETWEEN ? AND ?)+(SELECT COUNT(*) FROM points_projection WHERE board_month BETWEEN ? AND ?)+(SELECT COUNT(*) FROM learning_reward_rule WHERE board_month BETWEEN ? AND ?)",Integer.class,first,last,first,last,first,last);
            if(occupied==0)return start;
        }
        throw new IllegalStateException("No isolated leap-year recovery period available");
    }
    @Test void maintenanceRecoversThreeSilentMonthsAcrossNewYearAndLeapFebruaryWithoutDuplicates() {
        new TransactionTemplate(manager).executeWithoutResult(rollback->{
            rollback.setRollbackOnly();YearMonth start=unusedLeapRecoveryPeriod();String boundary=jdbc.queryForObject("SELECT start_month FROM learning_rollout_boundary WHERE id=1",String.class);
            String before=LearningSeasonService.parseMonth(boundary).minusMonths(1).format(LearningSeasonService.MONTH);
            int beforeRows=jdbc.queryForObject("SELECT COUNT(*) FROM learning_season WHERE board_month=?",Integer.class,before);
            maintenanceAt(start.atDay(5).atStartOfDay(),"重启前昵称").maintain();
            assertEquals("OPEN",jdbc.queryForObject("SELECT state FROM learning_season WHERE board_month=?",String.class,start.format(LearningSeasonService.MONTH)));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_season WHERE board_month=?",Integer.class,start.plusMonths(1).format(LearningSeasonService.MONTH)));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_season WHERE board_month=?",Integer.class,start.plusMonths(2).format(LearningSeasonService.MONTH)));
            // No invocation for three months. A new service instance compensates even months with no point facts.
            var restored=maintenanceAt(start.plusMonths(3).atDay(3).atStartOfDay(),"重启后昵称");restored.maintain();
            for(int offset=0;offset<=4;offset++)assertEquals(offset<3?"SETTLED":"OPEN",jdbc.queryForObject("SELECT state FROM learning_season WHERE board_month=?",String.class,start.plusMonths(offset).format(LearningSeasonService.MONTH)));
            var february=start.plusMonths(3);assertEquals(29,february.lengthOfMonth());
            assertEquals(february.plusMonths(1).atDay(2).atStartOfDay().toString().replace('T',' ')+":00",jdbc.queryForObject("SELECT DATE_FORMAT(freeze_at,'%Y-%m-%d %H:%i:%s') FROM learning_season WHERE board_month=?",String.class,february.format(LearningSeasonService.MONTH)));
            String first=start.format(LearningSeasonService.MONTH),last=start.plusMonths(4).format(LearningSeasonService.MONTH);
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_season_rank WHERE board_month BETWEEN ? AND ?",Integer.class,first,last));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_reward_request WHERE board_month BETWEEN ? AND ?",Integer.class,first,last));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_achievement WHERE season_month BETWEEN ? AND ?",Integer.class,first,last));
            var frozen=jdbc.queryForList("SELECT board_month,state,CAST(freeze_at AS CHAR) freeze_at,CAST(settled_at AS CHAR) settled_at FROM learning_season WHERE board_month BETWEEN ? AND ? ORDER BY board_month",first,last);
            restored.maintain();assertEquals(frozen,jdbc.queryForList("SELECT board_month,state,CAST(freeze_at AS CHAR) freeze_at,CAST(settled_at AS CHAR) settled_at FROM learning_season WHERE board_month BETWEEN ? AND ? ORDER BY board_month",first,last));
            assertEquals(boundary,jdbc.queryForObject("SELECT start_month FROM learning_rollout_boundary WHERE id=1",String.class));
            assertEquals(beforeRows,jdbc.queryForObject("SELECT COUNT(*) FROM learning_season WHERE board_month=?",Integer.class,before));
        });
    }
    @Test void restartedMaintenanceCreatesOneSnapshotRewardOutboxAndKeepsFrozenResultsAfterLatePoints() {
        new TransactionTemplate(manager).executeWithoutResult(rollback->{
            rollback.setRollbackOnly();var start=unusedLeapRecoveryPeriod();var earned=start.plusMonths(1);String value=earned.format(LearningSeasonService.MONTH);
            var recoveryTime=start.plusMonths(3).atDay(3).atStartOfDay();var fixed=Clock.fixed(recoveryTime.atZone(ZoneId.of("Asia/Shanghai")).toInstant(),ZoneId.of("Asia/Shanghai"));
            var ledger=new PointsRecordServiceImpl(jdbc,inbox,fixed);long rule=IdWorker.getId();
            jdbc.update("INSERT INTO learning_reward_rule(id,board_month,rank_from,rank_to,coupon_id,enabled,created_by) VALUES(?,?,1,1,99,1,?)",rule,value,user);
            ledger.award(user,10,PointsRecordType.LEARNING,"restart-first:"+user,earned.atDay(3).atTime(10,0));
            var restarted=maintenanceAt(recoveryTime,"补偿结算昵称");restarted.maintain();restarted.hydrateNames();
            assertEquals("SETTLED",jdbc.queryForObject("SELECT state FROM learning_season WHERE board_month=?",String.class,value));
            var snapshot=jdbc.queryForList("SELECT user_id,rank_no,points,display_name,CAST(reached_at AS CHAR) reached_at FROM learning_season_rank WHERE board_month=?",value);
            assertEquals(1,snapshot.size());assertEquals("补偿结算昵称",snapshot.getFirst().get("display_name"));
            String request=jdbc.queryForObject("SELECT request_id FROM learning_reward_request WHERE user_id=? AND board_month=?",String.class,user,value);
            String event=jdbc.queryForObject("SELECT event_id FROM reliability_outbox WHERE business_key=?",String.class,"season-reward:"+request+":1");
            ledger.award(user,10,PointsRecordType.LEARNING,"restart-late:"+user,earned.atDay(4).atTime(10,0));
            maintenanceAt(recoveryTime.plusHours(1),"结算后修改的昵称").maintain();
            assertEquals(snapshot,jdbc.queryForList("SELECT user_id,rank_no,points,display_name,CAST(reached_at AS CHAR) reached_at FROM learning_season_rank WHERE board_month=?",value));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_reward_request WHERE user_id=? AND board_month=?",Integer.class,user,value));
            assertEquals(event,jdbc.queryForObject("SELECT event_id FROM reliability_outbox WHERE business_key=?",String.class,"season-reward:"+request+":1"));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM reliability_outbox WHERE business_key LIKE ?",Integer.class,"season-reward:"+request+":%"));
            assertEquals(20,jdbc.queryForObject("SELECT SUM(points) FROM points_record WHERE user_id=?",Integer.class,user));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_points_reconciliation WHERE user_id=? AND board_month=?",Integer.class,user,value));
        });
    }
    @Test void maintenanceRetainsLegacyPointFactsWithoutMintingHistoricalRewardsOrHonours() {
        new TransactionTemplate(manager).executeWithoutResult(rollback->{
            rollback.setRollbackOnly();var boundary=LearningSeasonService.parseMonth(jdbc.queryForObject("SELECT start_month FROM learning_rollout_boundary WHERE id=1",String.class));
            var legacy=boundary.minusMonths(1);String value=legacy.format(LearningSeasonService.MONTH);long rule=IdWorker.getId();
            jdbc.update("INSERT INTO points_projection(board_month,user_id,points,version,processed_version,reached_at) VALUES(?,?,55,1,0,?)",value,user,legacy.atDay(3).atStartOfDay());
            jdbc.update("INSERT INTO learning_reward_rule(id,board_month,rank_from,rank_to,coupon_id,enabled,created_by) VALUES(?,?,1,1000,99,1,?)",rule,value,user);
            maintenanceAt(boundary.plusMonths(4).atDay(3).atStartOfDay(),"旧积分昵称").maintain();
            assertEquals("LEGACY_READONLY",jdbc.queryForObject("SELECT state FROM learning_season WHERE board_month=?",String.class,value));
            assertEquals(55,jdbc.queryForObject("SELECT points FROM points_projection WHERE board_month=? AND user_id=?",Integer.class,value,user));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM points_record WHERE user_id=?",Integer.class,user));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_achievement WHERE user_id=?",Integer.class,user));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_reward_request WHERE user_id=?",Integer.class,user));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_season_rank WHERE board_month=? AND user_id=?",Integer.class,value,user));
        });
    }

}
