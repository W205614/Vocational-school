package com.tianji.trade.service;
import com.tianji.common.exceptions.ConflictException;
import com.tianji.common.utils.UserContext;
import com.tianji.trade.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class OrderDeletionDatabaseTest {
 JdbcTemplate jdbc;TransactionTemplate tx;OrderServiceImpl service;long order;
 @BeforeEach void setup() {
  var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_trade?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
  jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));service=mock(OrderServiceImpl.class,CALLS_REAL_METHODS);ReflectionTestUtils.setField(service,"jdbc",jdbc);
  order=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();jdbc.update("INSERT INTO `order`(id,user_id,status,total_amount,real_amount,discount_amount,creater,updater) VALUES(?,7,3,100,100,0,7,7)",order);
 }
 @AfterEach void cleanup(){jdbc.update("DELETE FROM payment_conflict WHERE order_id=?",order);jdbc.update("DELETE FROM payment_fact WHERE order_id=?",order);jdbc.update("DELETE FROM `order` WHERE id=?",order);}
 @Test void deletionAndLatePaymentKeepBothRetainedOrderAndFinancialFact()throws Exception {
  var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var watched=spy(jdbc);
  doAnswer(call->{var rows=call.callRealMethod();locked.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return rows;}).when(watched).queryForList("SELECT id,user_id,status,deleted FROM `order` WHERE id=? FOR UPDATE",order);
  ReflectionTestUtils.setField(service,"jdbc",watched);
  var mapper=mock(com.tianji.trade.mapper.OrderMapper.class);
  when(mapper.selectRetainedForUpdate(order)).thenAnswer(call->{var row=jdbc.queryForMap("SELECT id,user_id,status,pay_order_no,deleted FROM `order` WHERE id=? FOR UPDATE",order);var retained=new com.tianji.trade.domain.po.Order();retained.setId(order);retained.setUserId(((Number)row.get("user_id")).longValue());retained.setStatus(((Number)row.get("status")).intValue());retained.setDeleted(((Number)row.get("deleted")).intValue());return retained;});
  ReflectionTestUtils.setField(service,"baseMapper",mapper);
  var fact=com.tianji.pay.sdk.dto.PayResultDTO.builder().bizOrderId(order).payOrderNo(order+1).status(com.tianji.pay.sdk.dto.PayResultDTO.SUCCESS).payChannel("mockPay").successTime(java.time.LocalDateTime.now()).build();
  try(var pool=Executors.newFixedThreadPool(2)) {
   var deletion=pool.submit(()->{UserContext.setUser(7L);try{tx.executeWithoutResult(status->service.deleteOrder(order));}finally{UserContext.removeUser();}});
   assertTrue(locked.await(5,TimeUnit.SECONDS));var payment=pool.submit(()->tx.executeWithoutResult(status->service.handlePaySuccess(fact)));
   try{assertThrows(TimeoutException.class,()->payment.get(100,TimeUnit.MILLISECONDS));}finally{release.countDown();}
   deletion.get(5,TimeUnit.SECONDS);payment.get(5,TimeUnit.SECONDS);
   assertEquals(1,jdbc.queryForObject("SELECT deleted FROM `order` WHERE id=?",Integer.class,order));assertEquals(3,jdbc.queryForObject("SELECT status FROM `order` WHERE id=?",Integer.class,order));
   assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM payment_fact WHERE order_id=?",Integer.class,order));assertEquals("PAYMENT_AFTER_STATE_CHANGE",jdbc.queryForObject("SELECT reason FROM payment_conflict WHERE order_id=?",String.class,order));
  }
 }
 @Test void paymentOrRefundHoldingTheOrderRowPreventsAStaleDeletion()throws Exception {
  for(int financialStatus:new int[]{2,6,7}) {
   jdbc.update("UPDATE `order` SET status=3,deleted=0 WHERE id=?",order);
   var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
   try(var pool=Executors.newFixedThreadPool(2)) {
    var financial=pool.submit(()->tx.executeWithoutResult(status->{jdbc.queryForMap("SELECT id FROM `order` WHERE id=? FOR UPDATE",order);locked.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}jdbc.update("UPDATE `order` SET status=? WHERE id=?",financialStatus,order);}));
    assertTrue(locked.await(5,TimeUnit.SECONDS));
    var deletion=pool.submit(()->{UserContext.setUser(7L);try{return assertThrows(ConflictException.class,()->tx.executeWithoutResult(status->service.deleteOrder(order)));}finally{UserContext.removeUser();}});
    try{assertThrows(TimeoutException.class,()->deletion.get(100,TimeUnit.MILLISECONDS));}finally{release.countDown();}
    financial.get(5,TimeUnit.SECONDS);deletion.get(5,TimeUnit.SECONDS);
    assertEquals(0,jdbc.queryForObject("SELECT deleted FROM `order` WHERE id=?",Integer.class,order));
    assertEquals(financialStatus,jdbc.queryForObject("SELECT status FROM `order` WHERE id=?",Integer.class,order));
   }
  }
 }
}
