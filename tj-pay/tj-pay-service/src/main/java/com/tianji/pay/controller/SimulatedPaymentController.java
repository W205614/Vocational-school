package com.tianji.pay.controller;
import com.tianji.pay.service.impl.ProviderSettlementService;
import com.tianji.common.utils.UserContext;
import com.tianji.common.exceptions.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Map;
@RestController @RequiredArgsConstructor @ConditionalOnProperty(name="tj.pay.simulated",havingValue="true")
public class SimulatedPaymentController {
 private final JdbcTemplate jdbc;private final ProviderSettlementService settlement;
 @PostMapping("/api/v2/simulator/payments/{business}/confirm") @Transactional public Object confirm(@PathVariable long business){
  long user=UserContext.requireUser();
  var rows=jdbc.queryForList("SELECT pay_order_no,amount FROM pay_order WHERE biz_order_no=? AND biz_user_id=? AND pay_channel_code='mockPay' FOR UPDATE",business,user);
  if(rows.isEmpty())throw new BadRequestException("模拟支付单不存在");
  var row=rows.getFirst();long number=((Number)row.get("pay_order_no")).longValue();int amount=((Number)row.get("amount")).intValue();
  if(jdbc.update("UPDATE simulated_provider_payment SET status=3,success_time=COALESCE(success_time,NOW(3)) WHERE pay_order_no=?",number)!=1)throw new BadRequestException("尚未创建模拟支付请求");
  Object stored=jdbc.queryForObject("SELECT success_time FROM simulated_provider_payment WHERE pay_order_no=?",Object.class,number);
  LocalDateTime time=stored instanceof LocalDateTime date?date:((java.sql.Timestamp)stored).toLocalDateTime();
  settlement.paid(number,amount,time);
  return Map.of("status","PAID","mode","SIMULATED");
 }
}
