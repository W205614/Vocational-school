package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.autoconfigure.reliability.OutboxStore;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.promotion.domain.po.*;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.mapper.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real InnoDB transaction/stock/receipt/outbox tests. Mapper bridges use the same JDBC connection. */
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class SeasonRewardDeliveryReliabilityTest {
    private static DataSource source;
    private JdbcTemplate jdbc;private CouponMapper coupons;private UserCouponMapper tickets;private OutboxStore outbox;private SeasonRewardDelivery delivery;
    private long couponId,user,rule;private String requestId;
    @BeforeAll static void createDedicatedSchema()throws Exception{
        String server="jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/";
        var admin=new JdbcTemplate(new DriverManagerDataSource(server,"root",System.getenv("ACCEPTANCE_DB_PASSWORD")));admin.execute("CREATE DATABASE IF NOT EXISTS acceptance_promotion");
        source=new DriverManagerDataSource(server+"acceptance_promotion?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
        Path repo=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(repo!=null && !Files.exists(repo.resolve("deploy/compact/schema.sql")))repo=repo.getParent();assertNotNull(repo,"Repository schema fixture must be available");
        String schema=Files.readString(repo.resolve("deploy/compact/schema.sql"));
        for(String table:List.of("coupon","user_coupon")){
            var match=Pattern.compile("CREATE TABLE `"+table+"` \\(.*?\\) ENGINE=.*?;",Pattern.DOTALL).matcher(schema);assertTrue(match.find(),"Missing production table "+table);apply(match.group().replaceFirst("CREATE TABLE","CREATE TABLE IF NOT EXISTS"));
        }
        apply(Files.readString(repo.resolve("deploy/acceptance/migrations/common/V001__reliability.sql")));
        apply(Files.readString(repo.resolve("deploy/acceptance/migrations/tj_promotion/V004__season_reward_delivery.sql")).replaceFirst("CREATE TABLE","CREATE TABLE IF NOT EXISTS"));
    }
    private static void apply(String sql){new ResourceDatabasePopulator(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8))).execute(source);}
    @BeforeEach void setup(){
        jdbc=new JdbcTemplate(source);coupons=mock(CouponMapper.class);tickets=mock(UserCouponMapper.class);outbox=spy(new OutboxStore(jdbc,JsonMapper.builder().build()));
        delivery=new SeasonRewardDelivery(jdbc,coupons,tickets,outbox,new DataSourceTransactionManager(source));
        couponId=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();user=couponId+1;rule=couponId+2;requestId="reward-"+couponId;
        LocalDateTime now=LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        jdbc.update("INSERT INTO coupon(id,discount_type,status,total_num,issue_num,user_limit,issue_begin_time,issue_end_time,term_days,creater,updater) VALUES(?,1,3,10,0,5,?,?,30,1,1)",couponId,now.minusDays(1),now.plusDays(1));
        when(coupons.selectOne(any())).thenAnswer(call->{
            QueryWrapper<Coupon> wrapper=call.getArgument(0);wrapper.getSqlSegment();assertTrue(wrapper.getSqlSegment().endsWith("FOR UPDATE"));
            long selected=((Number)wrapper.getParamNameValuePairs().values().iterator().next()).longValue();
            var rows=jdbc.query("SELECT * FROM coupon WHERE id=? FOR UPDATE",(rs,n)->new Coupon().setId(rs.getLong("id")).setStatus(CouponStatus.of(rs.getInt("status"))).setTotalNum(rs.getInt("total_num")).setIssueNum(rs.getInt("issue_num")).setUserLimit(rs.getInt("user_limit")).setIssueBeginTime(rs.getTimestamp("issue_begin_time").toLocalDateTime()).setIssueEndTime(rs.getTimestamp("issue_end_time").toLocalDateTime()).setTermDays(rs.getInt("term_days")),selected);
            return rows.isEmpty()?null:rows.getFirst();
        });
        when(coupons.incrIssueNum(anyLong())).thenAnswer(call->jdbc.update("UPDATE coupon SET issue_num=issue_num+1 WHERE id=? AND issue_num<total_num",call.<Long>getArgument(0)));
        doAnswer(call->{UserCoupon ticket=call.getArgument(0);return jdbc.update("INSERT INTO user_coupon(id,user_id,coupon_id,term_begin_time,term_end_time,status) VALUES(?,?,?,?,?,?)",ticket.getId(),ticket.getUserId(),ticket.getCouponId(),ticket.getTermBeginTime(),ticket.getTermEndTime(),ticket.getStatus().getValue());}).when(tickets).insert(any(UserCoupon.class));
    }
    private SeasonRewardDelivery.Request request(){return new SeasonRewardDelivery.Request(requestId,user,couponId,"202610",rule);}
    private Message message(String id){var props=new MessageProperties();props.setMessageId(id);return new Message(new byte[0],props);}
    private int stock(){return jdbc.queryForObject("SELECT issue_num FROM coupon WHERE id=?",Integer.class,couponId);}
    private int ticketCount(){return jdbc.queryForObject("SELECT COUNT(*) FROM user_coupon WHERE coupon_id=?",Integer.class,couponId);}
    private int receiptCount(){return jdbc.queryForObject("SELECT COUNT(*) FROM season_reward_delivery WHERE coupon_id=?",Integer.class,couponId);}
    private int ackCount(String routing){return jdbc.queryForObject("SELECT COUNT(*) FROM reliability_outbox WHERE routing_key=? AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.payload.requestId'))=?",Integer.class,routing,requestId);}
    @Test void duplicateAndDifferentRetryEventsProduceOneTicketAndOneAcknowledgement(){
        delivery.receive(request(),message("initial-"+couponId));delivery.receive(request(),message("initial-"+couponId));delivery.receive(request(),message("retry-"+couponId));
        assertEquals(1,stock());assertEquals(1,ticketCount());assertEquals(1,receiptCount());assertEquals(1,ackCount("learning.reward.fulfilled"));assertEquals(0,ackCount("learning.reward.failed"));
        var receipt=jdbc.queryForMap("SELECT status,user_coupon_id FROM season_reward_delivery WHERE request_id=?",requestId);assertEquals("SUCCEEDED",receipt.get("status"));assertEquals(receipt.get("user_coupon_id"),jdbc.queryForObject("SELECT id FROM user_coupon WHERE coupon_id=?",Long.class,couponId));
    }
    @Test void depletedStockFailsDurablyAndOnlyANewExplicitRetryCanDeliverAfterRestock(){
        jdbc.update("UPDATE coupon SET total_num=0 WHERE id=?",couponId);var original=message("empty-"+couponId);delivery.receive(request(),original);
        assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM season_reward_delivery WHERE request_id=?",String.class,requestId));assertEquals(0,stock());assertEquals(0,ticketCount());assertEquals(1,ackCount("learning.reward.failed"));
        jdbc.update("UPDATE coupon SET total_num=2 WHERE id=?",couponId);delivery.receive(request(),original);assertEquals(0,ticketCount());assertEquals(1,ackCount("learning.reward.failed"));
        delivery.receive(request(),message("explicit-retry-"+couponId));assertEquals(1,stock());assertEquals(1,ticketCount());assertEquals(1,ackCount("learning.reward.fulfilled"));assertEquals(1,ackCount("learning.reward.failed"));assertNull(jdbc.queryForObject("SELECT last_error FROM season_reward_delivery WHERE request_id=?",String.class,requestId));
    }
    @Test void acknowledgementWriteFailureRollsBackStockTicketAndReceiptTogether(){
        doAnswer(call->{call.callRealMethod();throw new IllegalStateException("Injected crash after durable ack insert");}).when(outbox).enqueue(anyString(),eq("learning.topic"),eq("learning.reward.fulfilled"),any());
        var message=message("atomic-"+couponId);assertThrows(IllegalStateException.class,()->delivery.receive(request(),message));assertEquals(0,stock());assertEquals(0,ticketCount());assertEquals(0,receiptCount());assertEquals(0,ackCount("learning.reward.fulfilled"));
        doCallRealMethod().when(outbox).enqueue(anyString(),eq("learning.topic"),eq("learning.reward.fulfilled"),any());delivery.receive(request(),message);assertEquals(1,stock());assertEquals(1,ticketCount());assertEquals(1,receiptCount());assertEquals(1,ackCount("learning.reward.fulfilled"));
    }
    @Test void redeliveryOfAnOlderFailedEventAfterAnotherFailedRetryRemainsIdempotent(){
        jdbc.update("UPDATE coupon SET total_num=0 WHERE id=?",couponId);var original=message("old-failed-"+couponId);var retry=message("new-failed-"+couponId);
        delivery.receive(request(),original);delivery.receive(request(),retry);assertEquals(2,ackCount("learning.reward.failed"));
        assertDoesNotThrow(()->delivery.receive(request(),original));assertEquals(2,ackCount("learning.reward.failed"));assertEquals(0,ticketCount());assertEquals(0,stock());
    }
    @Test void failureAcknowledgementCrashDoesNotStrandAFailedReceipt(){
        jdbc.update("UPDATE coupon SET total_num=0 WHERE id=?",couponId);
        doAnswer(call->{call.callRealMethod();throw new IllegalStateException("Injected failure ack crash");}).when(outbox).enqueue(anyString(),eq("learning.topic"),eq("learning.reward.failed"),any());
        var message=message("failed-atomic-"+couponId);assertThrows(IllegalStateException.class,()->delivery.receive(request(),message));assertEquals(0,stock());assertEquals(0,ticketCount());assertEquals(0,receiptCount());assertEquals(0,ackCount("learning.reward.failed"));
        doCallRealMethod().when(outbox).enqueue(anyString(),eq("learning.topic"),eq("learning.reward.failed"),any());delivery.receive(request(),message);assertEquals(1,receiptCount());assertEquals(1,ackCount("learning.reward.failed"));
    }
    @Test void concurrentRedeliveriesWithDifferentEventIdsCannotDoubleSpendStock()throws Exception{
        try(var pool=Executors.newFixedThreadPool(8)){List<Future<?>> work=new ArrayList<>();for(int i=0;i<24;i++){String event="parallel-"+couponId+"-"+i;work.add(pool.submit(()->delivery.receive(request(),message(event))));}for(var task:work)task.get(30,TimeUnit.SECONDS);}
        assertEquals(1,stock());assertEquals(1,ticketCount());assertEquals(1,receiptCount());assertEquals(1,ackCount("learning.reward.fulfilled"));
    }
    @Test void competingStudentsCannotOverdrawTheLastCoupon()throws Exception{
        jdbc.update("UPDATE coupon SET total_num=1 WHERE id=?",couponId);var first=request();var second=new SeasonRewardDelivery.Request(requestId+"-other",user+20,couponId,"202610",rule);
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->delivery.receive(first,message("last-a-"+couponId)));var b=pool.submit(()->delivery.receive(second,message("last-b-"+couponId)));a.get(30,TimeUnit.SECONDS);b.get(30,TimeUnit.SECONDS);}
        assertEquals(1,stock());assertEquals(1,ticketCount());assertEquals(2,receiptCount());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM season_reward_delivery WHERE coupon_id=? AND status='SUCCEEDED'",Integer.class,couponId));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM season_reward_delivery WHERE coupon_id=? AND status='FAILED'",Integer.class,couponId));
    }
    @Test void requestIdentityAndSeasonRuleUniquenessCannotBeReassigned(){
        delivery.receive(request(),message("owner-"+couponId));
        var changed=new SeasonRewardDelivery.Request(requestId,user+100,couponId,"202610",rule);assertThrows(BadRequestException.class,()->delivery.receive(changed,message("changed-"+couponId)));
        var collision=new SeasonRewardDelivery.Request(requestId+"-collision",user,couponId,"202610",rule);assertThrows(BadRequestException.class,()->delivery.receive(collision,message("collision-"+couponId)));
        assertEquals(1,stock());assertEquals(1,ticketCount());assertEquals(1,receiptCount());assertEquals(1,ackCount("learning.reward.fulfilled"));
    }
}
