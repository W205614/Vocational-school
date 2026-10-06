package com.tianji.pay.reliability;
import com.tianji.pay.service.impl.ProviderSettlementService;
import com.tianji.common.autoconfigure.reliability.OutboxStore;
import com.tianji.common.exceptions.BadRequestException;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.*;import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;import java.util.*;import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class ProviderSettlementTest {
 JdbcTemplate jdbc;TransactionTemplate tx;ProviderSettlementService service;
 @BeforeEach void setup(){
  var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:23316/acceptance_pay?serverTimezone=Asia/Shanghai","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
  jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));
  service=new ProviderSettlementService(jdbc,new OutboxStore(jdbc,JsonMapper.builder().build()));
  for(String table:List.of("reliability_outbox","provider_payment_fact","provider_refund_fact","provider_refund_conflict","refund_order","pay_order"))jdbc.update("DELETE FROM "+table);
  jdbc.update("INSERT INTO pay_order(id,biz_order_no,pay_order_no,biz_user_id,pay_channel_code,amount,status,pay_over_time) VALUES(100,300,200,1,'fixture',1000,1,NOW()+INTERVAL 1 HOUR)");
  jdbc.update("INSERT INTO refund_order(id,biz_order_no,biz_refund_order_no,pay_order_no,refund_order_no,refund_amount,total_amount,status) VALUES(101,300,500,200,400,250,1000,1)");
 }
 @Test void oneHundredConcurrentNotificationsCommitOneFactAndEvent()throws Exception{
  try(var pool=Executors.newFixedThreadPool(20)){
   List<Future<?>> futures=new ArrayList<>();
   for(int i=0;i<100;i++)futures.add(pool.submit(()->tx.executeWithoutResult(s->service.paid(200L,1000,LocalDateTime.now()))));
   for(var future:futures)future.get(30,TimeUnit.SECONDS);
  }
  assertEquals(1,count("provider_payment_fact"));assertEquals(1,count("reliability_outbox"));
  assertEquals(3,jdbc.queryForObject("SELECT status FROM pay_order WHERE id=100",Integer.class));assertNotNull(service.paidTime(200));
 }
 @Test void closedPaymentKeepsStateAndEmitsVerifiedLateFact(){
  jdbc.update("UPDATE pay_order SET status=2 WHERE id=100");
  tx.executeWithoutResult(s->service.paid(200L,1000,LocalDateTime.now()));
  assertEquals(2,jdbc.queryForObject("SELECT status FROM pay_order WHERE id=100",Integer.class));
  assertEquals(1,count("provider_payment_fact"));assertEquals(1,count("reliability_outbox"));
 }
 @Test void invalidAmountCommitsNoFactOrEvent(){
  assertThrows(BadRequestException.class,()->tx.executeWithoutResult(s->service.paid(200L,999,LocalDateTime.now())));
  assertEquals(0,count("provider_payment_fact"));assertEquals(0,count("reliability_outbox"));
 }
 @Test void failedRefundIsNotSuccessAndLateConflictCannotOverwriteTerminal(){
  tx.executeWithoutResult(s->service.refund(400L,3,"fixture","failed",250));
  String payload=jdbc.queryForObject("SELECT payload FROM reliability_outbox",String.class);
  assertEquals(2,JsonMapper.builder().build().readTree(payload).get("payload").get("status").asInt());
  tx.executeWithoutResult(s->service.refund(400L,2,"fixture","late success",250));
  assertEquals(3,jdbc.queryForObject("SELECT status FROM refund_order WHERE id=101",Integer.class));
  assertEquals(1,count("provider_refund_conflict"));assertEquals(1,count("reliability_outbox"));
 }
 int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
}
