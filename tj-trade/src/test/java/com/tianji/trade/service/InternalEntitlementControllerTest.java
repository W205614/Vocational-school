package com.tianji.trade.service;
import com.tianji.trade.controller.InternalEntitlementController;
import com.tianji.common.exceptions.ConflictException;
import com.tianji.common.utils.InternalAuth;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InternalEntitlementControllerTest {
 @Test void conflictingFiniteSnapshotCannotBecomeAPermanentLegacyEntitlement()throws Exception {
  var row=mock(ResultSet.class);var purchased=Timestamp.valueOf(LocalDateTime.now());
  when(row.getTimestamp("purchased_at")).thenReturn(purchased);when(row.getInt("status")).thenReturn(2);
  when(row.getTimestamp("course_expire_time")).thenReturn(purchased);
  var jdbc=new JdbcTemplate(){@Override public <T> List<T> query(String sql,RowMapper<T> mapper,Object... args){try{return List.of(mapper.mapRow(row,0));}catch(java.sql.SQLException error){throw new RuntimeException(error);}}};
  var controller=new InternalEntitlementController(jdbc);
  try(var auth=mockStatic(InternalAuth.class)){
   assertThrows(ConflictException.class,()->controller.entitlements(41));
   when(row.getObject("valid_duration")).thenReturn(0);
   assertThrows(ConflictException.class,()->controller.entitlements(41));
   when(row.getObject("valid_duration")).thenReturn(1);when(row.getInt("valid_duration")).thenReturn(1);
   assertEquals(1,controller.entitlements(41).getFirst().validDuration());
   when(row.getObject("valid_duration")).thenReturn(null);when(row.getInt("valid_duration")).thenReturn(0);
   when(row.getTimestamp("course_expire_time")).thenReturn(null);
   assertEquals(0,controller.entitlements(41).getFirst().validDuration());
   when(row.getTimestamp("purchased_at")).thenReturn(null);
   assertThrows(ConflictException.class,()->controller.entitlements(41));
  }
 }
}
