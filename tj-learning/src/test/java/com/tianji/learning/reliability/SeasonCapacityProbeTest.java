package com.tianji.learning.reliability;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.learning.service.impl.LearningSeasonService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** A single controlled 1000-row diagnostic, explicitly selected by the acceptance runner.
 * The printed timings are observations, never a throughput or SLA claim. */
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class SeasonCapacityProbeTest {
    @Test void compareOneRankingReadAndExclusiveVersusSharedMonthFence() throws Exception {
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_learning?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&rewriteBatchedStatements=true","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
        var jdbc=new JdbcTemplate(source);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        assertEquals("acceptance_learning",jdbc.queryForObject("SELECT DATABASE()",String.class));
        YearMonth period=null;
        for(int index=0;index<24;index++) {
            var candidate=YearMonth.of(9000+ThreadLocalRandom.current().nextInt(900),1+ThreadLocalRandom.current().nextInt(12));
            String month=candidate.format(LearningSeasonService.MONTH);
            if(jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM learning_season WHERE board_month=?)+(SELECT COUNT(*) FROM points_projection WHERE board_month=?)",Integer.class,month,month)==0){period=candidate;break;}
        }
        assertNotNull(period,"Could not reserve a synthetic diagnostic month");
        final var selected=period;final String month=selected.format(LearningSeasonService.MONTH);
        LearningSeasonService.ensureSeason(jdbc,selected);
        var evidence=new LinkedHashMap<String,Object>();evidence.put("rows",1000);evidence.put("boundary","One synthetic acceptance database sample; no production capacity claim");
        try {
            tx.executeWithoutResult(status->{
                status.setRollbackOnly();var users=new ArrayList<Long>();for(int index=0;index<1000;index++)users.add(IdWorker.getId());
                jdbc.batchUpdate("INSERT INTO points_projection(board_month,user_id,points,version,processed_version,reached_at) VALUES(?,?,?,1,0,?)",users,1000,(statement,user)->{
                    int index=users.indexOf(user);statement.setString(1,month);statement.setLong(2,user);statement.setInt(3,10+(index%50)*10);statement.setObject(4,selected.atDay(3).atStartOfDay().plusSeconds(index));
                });
                long user=users.get(500);
                String ranked="SELECT user_id,points,reached_at,ROW_NUMBER() OVER(ORDER BY points DESC,reached_at ASC,user_id ASC) rank_no FROM points_projection WHERE board_month=? AND points>0";
                long before=System.nanoTime();
                var mine=jdbc.queryForMap("SELECT * FROM ("+ranked+") ranked WHERE user_id=?",month,user);
                var oldPage=jdbc.queryForList("SELECT user_id,points FROM ("+ranked+") ranked ORDER BY rank_no LIMIT 20",month);
                long oldTotal=jdbc.queryForObject("SELECT COUNT(*) FROM ("+ranked+") ranked",Long.class,month);
                evidence.put("legacyReadNanos",System.nanoTime()-before);
                String pageSql="SELECT user_id,points FROM points_projection WHERE board_month=? AND points>0 ORDER BY points DESC,reached_at ASC,user_id ASC LIMIT 20";
                before=System.nanoTime();
                var current=jdbc.queryForMap("SELECT points,reached_at FROM points_projection WHERE board_month=? AND user_id=?",month,user);
                long newRank=1+jdbc.queryForObject("SELECT COUNT(*) FROM points_projection WHERE board_month=? AND (points>? OR (points=? AND (reached_at<? OR (reached_at=? AND user_id<?))))",Long.class,month,current.get("points"),current.get("points"),current.get("reached_at"),current.get("reached_at"),user);
                var newPage=jdbc.queryForList(pageSql,month);
                long newTotal=jdbc.queryForObject("SELECT COUNT(*) FROM points_projection WHERE board_month=? AND points>0",Long.class,month);
                evidence.put("boundedReadNanos",System.nanoTime()-before);
                assertEquals(((Number)mine.get("rank_no")).longValue(),newRank);assertEquals(oldTotal,newTotal);assertEquals(oldPage,newPage);
                evidence.put("legacyPlan",jdbc.queryForObject("EXPLAIN FORMAT=TREE SELECT user_id,points FROM ("+ranked+") ranked ORDER BY rank_no LIMIT 20",String.class,month));
                evidence.put("pagePlan",jdbc.queryForObject("EXPLAIN FORMAT=TREE "+pageSql,String.class,month));
            });
            evidence.put("exclusiveFenceBlocksDifferentLearner",probeFence(jdbc,tx,month,false));
            evidence.put("sharedFenceAllowsDifferentLearner",probeFence(jdbc,tx,month,true));
            System.out.println("SEASON_CAPACITY_PROBE "+JsonMapper.builder().build().writeValueAsString(evidence));
        } finally {
            // The queried projection fixtures rolled back; delete only this reserved, empty month.
            jdbc.update("DELETE FROM learning_season WHERE board_month=? AND NOT EXISTS(SELECT 1 FROM points_projection WHERE board_month=?) AND NOT EXISTS(SELECT 1 FROM learning_season_rank WHERE board_month=?)",month,month,month);
        }
    }
    private boolean probeFence(JdbcTemplate jdbc,TransactionTemplate tx,String month,boolean shared) throws Exception {
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var owner=pool.submit(()->tx.executeWithoutResult(status->{
                jdbc.queryForObject("SELECT state FROM learning_season WHERE board_month=? "+(shared?"FOR SHARE":"FOR UPDATE"),String.class,month);held.countDown();
                try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Fence probe timeout");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
            }));
            try {
                assertTrue(held.await(5,TimeUnit.SECONDS));
                var differentLearner=pool.submit(()->tx.execute(status->jdbc.queryForObject("SELECT state FROM learning_season WHERE board_month=? FOR SHARE",String.class,month)));
                if(shared)assertEquals("OPEN",differentLearner.get(5,TimeUnit.SECONDS));
                else assertThrows(TimeoutException.class,()->differentLearner.get(200,TimeUnit.MILLISECONDS));
                release.countDown();owner.get(5,TimeUnit.SECONDS);differentLearner.get(5,TimeUnit.SECONDS);return true;
            } finally {release.countDown();}
        }
    }
}
