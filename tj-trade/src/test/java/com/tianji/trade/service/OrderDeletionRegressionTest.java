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
}
