package com.tianji.pay.reliability;
import com.tianji.pay.service.impl.RefundOrderServiceImpl;
import com.tianji.pay.service.IPayOrderService;
import com.tianji.pay.domain.po.*;
import com.tianji.pay.mapper.RefundOrderMapper;
import com.tianji.pay.sdk.dto.RefundApplyDTO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RefundInitialStateTest {
 @Test void firstPreparedRefundIsImmediatelyEligibleForTheProviderCall() {
  var service=mock(RefundOrderServiceImpl.class,CALLS_REAL_METHODS);
  var payments=mock(IPayOrderService.class);var jdbc=mock(JdbcTemplate.class);var mapper=mock(RefundOrderMapper.class);
  var paid=new PayOrder();paid.setStatus(3);paid.setAmount(100);paid.setPayOrderNo(20L);paid.setPayChannelCode("mockPay");
  when(payments.queryByBizOrderNo(10L)).thenReturn(paid);
  when(jdbc.queryForObject("SELECT COALESCE(SUM(refund_amount),0) FROM refund_order WHERE biz_order_no=? AND status IN(0,1,2)",Long.class,10L)).thenReturn(0L);
  ReflectionTestUtils.setField(service,"payOrderService",payments);ReflectionTestUtils.setField(service,"jdbc",jdbc);ReflectionTestUtils.setField(service,"baseMapper",mapper);
  var query=mock(com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper.class,RETURNS_SELF);doReturn(query).when(service).lambdaQuery();
  doReturn(query).when(query).eq(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class),eq(11L));
  doReturn(true).when(service).save(org.mockito.ArgumentMatchers.any(RefundOrder.class));
  var request=RefundApplyDTO.builder().bizOrderNo(10L).bizRefundOrderNo(11L).refundAmount(100).build();
  RefundOrder prepared=ReflectionTestUtils.invokeMethod(service,"checkIdempotent",request);
  assertNotNull(prepared);assertEquals(0,prepared.getStatus());assertTrue(prepared.notCommit());
 }
}
