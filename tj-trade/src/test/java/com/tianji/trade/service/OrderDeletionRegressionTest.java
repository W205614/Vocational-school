package com.tianji.trade.service;

import com.tianji.common.exceptions.ConflictException;
import com.tianji.common.utils.UserContext;
import com.tianji.trade.domain.po.Order;
import com.tianji.trade.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderDeletionRegressionTest {
 @AfterEach void clear(){UserContext.removeUser();}
 @Test void paidOrderCannotBeDeleted() {
  UserContext.setUser(7L);
  var service=Mockito.mock(OrderServiceImpl.class,Mockito.CALLS_REAL_METHODS);
  var order=new Order();order.setId(11L);order.setUserId(7L);order.setStatus(2);order.setDeleted(0);
  doReturn(order).when(service).getById(11L);doReturn(true).when(service).removeById(11L);
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList("SELECT id,user_id,status,deleted FROM `order` WHERE id=? FOR UPDATE",11L)).thenReturn(List.of(Map.of("id",11L,"user_id",7L,"status",2,"deleted",0)));
  ReflectionTestUtils.setField(service,"jdbc",jdbc);
  assertThrows(ConflictException.class,()->service.deleteOrder(11L));
  verify(service,never()).removeById(11L);
 }
 private OrderServiceImpl service(int status,int deleted,long owner) {
  var service=Mockito.mock(OrderServiceImpl.class,Mockito.CALLS_REAL_METHODS);
  var jdbc=mock(JdbcTemplate.class);
  when(jdbc.queryForList("SELECT id,user_id,status,deleted FROM `order` WHERE id=? FOR UPDATE",11L)).thenReturn(List.of(Map.of("id",11L,"user_id",owner,"status",status,"deleted",deleted)));
  when(jdbc.update("UPDATE `order` SET deleted=1 WHERE id=? AND status=3 AND deleted=0",11L)).thenReturn(1);
  ReflectionTestUtils.setField(service,"jdbc",jdbc);return service;
 }
 @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={1,2,4,5,6,7})
 void everyNonClosedStateIsRejected(int status) {
  UserContext.setUser(7L);assertThrows(ConflictException.class,()->service(status,0,7L).deleteOrder(11L));
 }
 @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={1,2,4,5,6,7})
 void legacyLogicalDeletionCannotBypassFinancialStateValidation(int status) {
  UserContext.setUser(7L);assertThrows(ConflictException.class,()->service(status,1,7L).deleteOrder(11L));
 }
 @Test void closedOrderAndRepeatedDeleteAreIdempotent() {
  UserContext.setUser(7L);assertDoesNotThrow(()->service(3,0,7L).deleteOrder(11L));assertDoesNotThrow(()->service(3,1,7L).deleteOrder(11L));
 }
 @Test void otherOwnersCannotDeleteEvenAnAlreadyDeletedOrder() {
  UserContext.setUser(7L);assertThrows(com.tianji.common.exceptions.ForbiddenException.class,()->service(3,1,8L).deleteOrder(11L));
 }
 @Test void latePaymentUsesRetainedFactsInsteadOfTheLogicalDeleteQuery() {
  var service=mock(OrderServiceImpl.class,CALLS_REAL_METHODS);var jdbc=mock(JdbcTemplate.class);var mapper=mock(com.tianji.trade.mapper.OrderMapper.class);
  var retained=new Order();retained.setId(11L);retained.setUserId(7L);retained.setStatus(3);retained.setDeleted(1);
  when(mapper.selectRetainedForUpdate(11L)).thenReturn(retained);
  when(jdbc.queryForMap("SELECT order_id FROM payment_fact WHERE pay_order_no=?",12L)).thenReturn(Map.of("order_id",11L));
  ReflectionTestUtils.setField(service,"jdbc",jdbc);ReflectionTestUtils.setField(service,"baseMapper",mapper);
  var fact=com.tianji.pay.sdk.dto.PayResultDTO.builder().bizOrderId(11L).payOrderNo(12L).status(com.tianji.pay.sdk.dto.PayResultDTO.SUCCESS).payChannel("mockPay").successTime(java.time.LocalDateTime.now()).build();
  assertDoesNotThrow(()->service.handlePaySuccess(fact));
  verify(mapper).selectRetainedForUpdate(11L);
  verify(jdbc).update("INSERT IGNORE INTO payment_conflict(order_id,pay_order_no,reason,status) VALUES(?,?,?,'OPEN')",11L,12L,"PAYMENT_AFTER_STATE_CHANGE");
 }
}
