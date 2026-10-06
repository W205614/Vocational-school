package com.tianji.pay.third.local;
import com.tianji.pay.third.*;
import com.tianji.pay.third.model.*;
import com.tianji.common.exceptions.*;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
@Service("mockPay") @ConditionalOnProperty(name="tj.pay.simulated",havingValue="true")
public class SimulatedPayService implements IPayService {
 private final JdbcTemplate jdbc;
 public SimulatedPayService(JdbcTemplate jdbc,Environment env){this.jdbc=jdbc;if(Arrays.stream(env.getActiveProfiles()).noneMatch(Set.of("acceptance","local-simulator")::contains))throw new IllegalStateException("Simulated payment is forbidden outside explicit local profiles");}
 public PrepayResponse createPrepayOrder(String title,String number,Integer amount){
  jdbc.update("INSERT IGNORE INTO simulated_provider_payment(pay_order_no,amount) VALUES(?,?)",number,amount);
  if(!Objects.equals(jdbc.queryForObject("SELECT amount FROM simulated_provider_payment WHERE pay_order_no=?",Integer.class,number),amount))throw new ConflictException("模拟支付请求金额不同");
  long business=jdbc.queryForObject("SELECT biz_order_no FROM pay_order WHERE pay_order_no=?",Long.class,number);
  return PrepayResponse.builder().success(true).payUrl("/simulate-payment/"+business).build();
 }
 public PayStatusResponse queryPayOrderStatus(String number){
  var rows=jdbc.queryForList("SELECT amount,status,success_time FROM simulated_provider_payment WHERE pay_order_no=?",number);
  if(rows.isEmpty())return PayStatusResponse.builder().success(false).build();
  var row=rows.getFirst();Object time=row.get("success_time");
  return PayStatusResponse.builder().success(true).payOrderNo(number).payStatus(((Number)row.get("status")).intValue()).totalAmount(((Number)row.get("amount")).intValue()).successTime(time instanceof java.time.LocalDateTime date?date:time instanceof java.sql.Timestamp stamp?stamp.toLocalDateTime():null).build();
 }
 public RefundResponse refundOrder(String pay,String refund,Integer amount,Integer total){
  if(!Integer.valueOf(3).equals(jdbc.queryForObject("SELECT status FROM simulated_provider_payment WHERE pay_order_no=?",Integer.class,pay)))throw new BadRequestException("模拟支付尚未成功");
  jdbc.update("INSERT IGNORE INTO simulated_provider_refund(refund_order_no,pay_order_no,amount) VALUES(?,?,?)",refund,pay,amount);
  var row=jdbc.queryForMap("SELECT pay_order_no,amount FROM simulated_provider_refund WHERE refund_order_no=?",refund);
  if(((Number)row.get("pay_order_no")).longValue()!=Long.parseLong(pay) || ((Number)row.get("amount")).intValue()!=amount)throw new ConflictException("模拟退款请求内容不同");
  return queryRefundStatus(pay,refund);
 }
 public RefundResponse queryRefundStatus(String pay,String refund){
  var rows=jdbc.queryForList("SELECT * FROM simulated_provider_refund WHERE refund_order_no=? AND pay_order_no=?",refund,pay);
  if(rows.isEmpty())return RefundResponse.builder().success(true).status(0).build();
  return RefundResponse.builder().success(true).status(2).amount(((Number)rows.getFirst().get("amount")).intValue()).channel("SIMULATED").build();
 }
}
