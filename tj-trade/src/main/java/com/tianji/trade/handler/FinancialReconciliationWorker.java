package com.tianji.trade.handler;
import com.tianji.trade.service.*;
import com.tianji.pay.sdk.client.PayClient;
import com.tianji.pay.sdk.dto.PayResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.*;
@Component @RequiredArgsConstructor
public class FinancialReconciliationWorker {
 private final JdbcTemplate jdbc;private final IRefundApplyService refunds;private final IOrderService orders;private final PayClient payments;
 private final ThreadPoolTaskExecutor sendRefundRequestExecutor;
 @Scheduled(fixedDelayString="${tj.trade.financial-interval-ms:1000}") public void poll(){
  for(String table:List.of("refund_delivery_task","payment_reconcile")){
   String id=table.equals("refund_delivery_task")?"refund_id":"order_id";
   for(var row:jdbc.queryForList("SELECT * FROM "+table+" WHERE status IN('PENDING','SENDING') AND next_attempt_at<=NOW() ORDER BY next_attempt_at LIMIT 20")){
    try{sendRefundRequestExecutor.execute(()->process(table,id,row));}catch(java.util.concurrent.RejectedExecutionException full){return;}
   }
  }
 }
 private void process(String table,String column,Map<String,Object> row){
  Object id=row.get(column);String token=UUID.randomUUID().toString();
  if(jdbc.update("UPDATE "+table+" SET status='SENDING',lease_token=?,attempts=attempts+1,next_attempt_at=NOW()+INTERVAL 30 SECOND WHERE "+column+"=? AND status IN('PENDING','SENDING') AND next_attempt_at<=NOW()",token,id)!=1)return;
  try{
   boolean complete=false;
   if(table.equals("refund_delivery_task")){
    var apply=refunds.getById(((Number)id).longValue());
    if(apply==null || apply.getStatus()!=3)complete=true;
    else{if(!refunds.checkRefundStatus(apply))refunds.sendRefundRequest(apply);complete=refunds.getById(apply.getId()).getStatus()!=3;}
   }else{
    PayResultDTO result=payments.queryPayResult(((Number)id).longValue());
    if(result!=null && result.getStatus()==PayResultDTO.SUCCESS){orders.handlePaySuccess(result);complete=true;}
    if(!complete && !com.tianji.common.utils.JdbcTime.localDateTime(row.get("expires_at")).isAfter(java.time.LocalDateTime.now())){
     jdbc.update("UPDATE payment_reconcile SET status='DEAD',last_error='24 小时内未取得最终支付事实，需要人工核对',lease_token=NULL,version=version+1 WHERE order_id=? AND lease_token=?",id,token);return;
    }
   }
   jdbc.update("UPDATE "+table+" SET status=?,last_error=NULL,lease_token=NULL,next_attempt_at=NOW()+INTERVAL 30 SECOND,version=version+1 WHERE "+column+"=? AND lease_token=?",complete?"DONE":"PENDING",id,token);
  }catch(Exception error){
   int attempts=((Number)row.get("attempts")).intValue()+1;String reason=error.getClass().getSimpleName()+": "+Objects.toString(error.getMessage(),"");
   jdbc.update("UPDATE "+table+" SET status=?,last_error=?,lease_token=NULL,next_attempt_at=DATE_ADD(NOW(),INTERVAL ? SECOND),version=version+1 WHERE "+column+"=? AND lease_token=?",attempts>=10?"DEAD":"PENDING",reason.substring(0,Math.min(1000,reason.length())),Math.min(300,1<<Math.min(8,attempts)),id,token);
  }
 }
}
