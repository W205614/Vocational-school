package com.tianji.pay.service.impl;
import com.tianji.common.autoconfigure.reliability.OutboxStore;
import com.tianji.common.exceptions.*;
import com.tianji.common.constants.MqConstants;
import com.tianji.pay.sdk.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
/** Provider verification/query happens before these short, local transactions. */
@Service @RequiredArgsConstructor
public class ProviderSettlementService {
 private final JdbcTemplate jdbc;private final OutboxStore outbox;
 @Transactional public void paid(Long number,Integer amount,LocalDateTime occurred){
  if(number==null || amount==null || amount<=0)throw new BadRequestException("支付事实参数无效");
  var rows=jdbc.queryForList("SELECT * FROM pay_order WHERE pay_order_no=? FOR UPDATE",number);
  if(rows.isEmpty())throw new BadRequestException("支付单不存在");
  var order=rows.getFirst();
  if(((Number)order.get("amount")).intValue()!=amount)throw new BadRequestException("支付金额不一致");
  LocalDateTime time=occurred==null?LocalDateTime.now():occurred;
  if(jdbc.update("INSERT IGNORE INTO provider_payment_fact(pay_order_no,biz_order_no,amount,success_time) VALUES(?,?,?,?)",number,order.get("biz_order_no"),amount,time)==0)return;
  // A closed payment remains closed. The business service records the late payment for manual handling.
  jdbc.update("UPDATE pay_order SET status=3,pay_success_time=?,notify_status=0 WHERE id=? AND status IN(0,1)",time,order.get("id"));
  outbox.enqueue("provider:"+number+":paid",MqConstants.Exchange.PAY_EXCHANGE,MqConstants.Key.PAY_SUCCESS,
   PayResultDTO.builder().status(PayResultDTO.SUCCESS).payOrderNo(number).bizOrderId(((Number)order.get("biz_order_no")).longValue()).payChannel(Objects.toString(order.get("pay_channel_code"),"")).successTime(time).build());
 }
 public LocalDateTime paidTime(long number){
  var rows=jdbc.queryForList("SELECT success_time FROM provider_payment_fact WHERE pay_order_no=?",number);
  return rows.isEmpty()?null:(rows.getFirst().get("success_time") instanceof LocalDateTime time?time:((java.sql.Timestamp)rows.getFirst().get("success_time")).toLocalDateTime());
 }
 @Transactional public void refund(Long number,Integer state,String channel,String message,Integer amount){
  if(number==null || state==null || state!=2 && state!=3)return;
  var rows=jdbc.queryForList("SELECT * FROM refund_order WHERE refund_order_no=? FOR UPDATE",number);
  if(rows.isEmpty())throw new BadRequestException("退款单不存在");
  var order=rows.getFirst();
  if(amount!=null && ((Number)order.get("refund_amount")).intValue()!=amount)throw new BadRequestException("退款金额不一致");
  jdbc.update("INSERT IGNORE INTO provider_refund_fact(refund_order_no,status) VALUES(?,?)",number,state);
  int previous=((Number)order.get("status")).intValue();
  if(previous==2 || previous==3){
   if(previous!=state)jdbc.update("INSERT IGNORE INTO provider_refund_conflict(refund_order_no,observed_status,recorded_status) VALUES(?,?,?)",number,state,previous);
   return;
  }
  jdbc.update("UPDATE refund_order SET status=?,refund_channel=COALESCE(?,refund_channel),result_msg=? WHERE id=? AND status IN(0,1)",state,channel,message,order.get("id"));
  outbox.enqueue("provider:"+number+":refund:"+state,MqConstants.Exchange.PAY_EXCHANGE,MqConstants.Key.REFUND_CHANGE,
   RefundResultDTO.builder().status(state==2?RefundResultDTO.SUCCESS:RefundResultDTO.FAILED).bizPayOrderId(((Number)order.get("biz_order_no")).longValue()).bizRefundOrderId(((Number)order.get("biz_refund_order_no")).longValue()).refundOrderNo(number).refundChannel(channel==null?Objects.toString(order.get("refund_channel"),""):channel).msg(message).build());
 }
}
