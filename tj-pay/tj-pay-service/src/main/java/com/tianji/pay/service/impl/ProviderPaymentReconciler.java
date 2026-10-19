package com.tianji.pay.service.impl;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.JdbcTime;
import com.tianji.pay.domain.po.PayOrder;
import com.tianji.pay.third.IPayService;
import com.tianji.pay.third.model.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;

/** Provider IO is outside transactions; a closed local order still needs its financial fact. */
@Service
public class ProviderPaymentReconciler {
    private final JdbcTemplate jdbc;
    private final ProviderSettlementService settlement;
    private final Map<String,IPayService> channels;
    private final ThreadPoolTaskExecutor executor;
    private final TransactionTemplate tx;

    public ProviderPaymentReconciler(JdbcTemplate jdbc,ProviderSettlementService settlement,
            @Qualifier("payServiceChannels") Map<String,IPayService> channels,
            @Qualifier("providerReconciliationExecutor") ThreadPoolTaskExecutor executor,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.settlement=settlement;this.channels=channels;this.executor=executor;this.tx=new TransactionTemplate(manager);
    }

    public void ensure(long number) {
        jdbc.update("INSERT IGNORE INTO provider_payment_reconcile(pay_order_no,expires_at) SELECT pay_order_no,DATE_ADD(COALESCE(create_time,CURRENT_TIMESTAMP(3)),INTERVAL 7 DAY) FROM pay_order WHERE pay_order_no=?",number);
    }

    @Scheduled(fixedDelayString="${tj.pay.reconcile-interval-ms:1000}") public void poll() {
        // Also repairs a crash between committing a new provider request and registering its task.
        jdbc.update("INSERT IGNORE INTO provider_payment_reconcile(pay_order_no,expires_at) SELECT p.pay_order_no,DATE_ADD(COALESCE(p.create_time,CURRENT_TIMESTAMP(3)),INTERVAL 7 DAY) FROM pay_order p LEFT JOIN provider_payment_reconcile r ON r.pay_order_no=p.pay_order_no LEFT JOIN provider_payment_fact f ON f.pay_order_no=p.pay_order_no WHERE r.pay_order_no IS NULL AND f.pay_order_no IS NULL AND p.status IN(0,1,2) ORDER BY p.id LIMIT 100");
        for(var row:jdbc.queryForList("SELECT pay_order_no FROM provider_payment_reconcile WHERE status IN('PENDING','SENDING') AND next_attempt_at<=CURRENT_TIMESTAMP(3) ORDER BY next_attempt_at,pay_order_no LIMIT 100")) {
            long number=((Number)row.get("pay_order_no")).longValue();
            try {executor.execute(()->reconcile(number));}
            catch(java.util.concurrent.RejectedExecutionException full) {break;}
        }
    }

    public void reconcile(long number) {
        String token=UUID.randomUUID().toString();
        if(jdbc.update("UPDATE provider_payment_reconcile SET status='SENDING',attempts=attempts+1,version=version+1,lease_token=?,next_attempt_at=CURRENT_TIMESTAMP(3)+INTERVAL 30 SECOND WHERE pay_order_no=? AND status IN('PENDING','SENDING') AND next_attempt_at<=CURRENT_TIMESTAMP(3)",token,number)!=1)return;
        try {
            var task=jdbc.queryForMap("SELECT attempts,expires_at FROM provider_payment_reconcile WHERE pay_order_no=?",number);
            if(settlement.paidTime(number)!=null) {finish(number,token,"DONE",null,0);return;}
            var rows=jdbc.queryForList("SELECT * FROM pay_order WHERE pay_order_no=?",number);
            if(rows.isEmpty())throw new BadRequestException("支付单不存在");
            var row=rows.getFirst();
            PayOrder order=new PayOrder();order.setId(((Number)row.get("id")).longValue());order.setPayOrderNo(number);
            order.setAmount(((Number)row.get("amount")).intValue());order.setPayChannelCode(Objects.toString(row.get("pay_channel_code"),""));
            order.setPayOverTime(JdbcTime.localDateTime(row.get("pay_over_time")));
            order.setCreateTime(JdbcTime.localDateTime(row.get("create_time")));
            boolean paid=check(order);
            if(paid) {finish(number,token,"DONE",null,0);return;}
            if(!JdbcTime.localDateTime(task.get("expires_at")).isAfter(LocalDateTime.now())) {
                anomaly(order,"WINDOW_EXHAUSTED",null);
                finish(number,token,"DEAD","七天核对窗口结束，需要人工核对渠道账单",0);
            } else finish(number,token,"PENDING",null,delay(((Number)task.get("attempts")).intValue()));
        } catch(Exception error) {
            var task=jdbc.queryForMap("SELECT attempts,expires_at FROM provider_payment_reconcile WHERE pay_order_no=?",number);
            String reason=error.getClass().getSimpleName()+": "+Objects.toString(error.getMessage(),"");
            boolean expired=!JdbcTime.localDateTime(task.get("expires_at")).isAfter(LocalDateTime.now());
            finish(number,token,expired?"DEAD":"PENDING",reason,delay(((Number)task.get("attempts")).intValue()));
        }
    }

    /** Also used by the legacy XXL job, so it cannot bypass provider validation. */
    public boolean check(PayOrder order) {
        IPayService provider=channels.get(order.getPayChannelCode());
        if(provider==null) {anomaly(order,"CHANNEL_UNAVAILABLE",null);throw new BadRequestException("支付渠道不可用");}
        PayStatusResponse response=provider.queryPayOrderStatus(order.getPayOrderNo().toString());
        if(response==null || !response.isSuccess())throw new IllegalStateException("渠道查询未取得可信结果");
        if(!Objects.equals(order.getPayOrderNo().toString(),response.getPayOrderNo()))reject(order,"ORDER_NUMBER_MISMATCH",response);
        if(!Objects.equals(order.getAmount(),response.getTotalAmount()))reject(order,"AMOUNT_MISMATCH",response);
        if(PayStatus.TRADE_SUCCESS.equalsValue(response.getPayStatus())) {
            if(response.getSuccessTime()==null || response.getSuccessTime().isAfter(LocalDateTime.now().plusMinutes(5)) ||
                    order.getCreateTime()!=null && response.getSuccessTime().isBefore(order.getCreateTime().minusMinutes(5)))reject(order,"INVALID_SUCCESS_TIME",response);
            tx.executeWithoutResult(s->settlement.paid(order.getPayOrderNo(),response.getTotalAmount(),response.getSuccessTime()));
            return true;
        }
        if(!PayStatus.WAIT_BUYER_PAY.equalsValue(response.getPayStatus()) && !PayStatus.NOT_COMMIT.equalsValue(response.getPayStatus()) && !PayStatus.TRADE_CLOSED.equalsValue(response.getPayStatus()))reject(order,"UNKNOWN_STATUS",response);
        // Never close on query failure or before checking for a success at the provider.
        if(PayStatus.TRADE_CLOSED.equalsValue(response.getPayStatus()) || order.getPayOverTime()!=null && !order.getPayOverTime().isAfter(LocalDateTime.now()))
            jdbc.update("UPDATE pay_order SET status=2 WHERE pay_order_no=? AND status IN(0,1)",order.getPayOrderNo());
        return false;
    }

    private void reject(PayOrder order,String reason,PayStatusResponse response) {
        anomaly(order,reason,response);throw new BadRequestException("渠道支付事实校验失败: "+reason);
    }
    private void anomaly(PayOrder order,String reason,PayStatusResponse response) {
        jdbc.update("INSERT INTO provider_payment_anomaly(pay_order_no,reason,expected_amount,observed_order_no,observed_amount,observed_time) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE status='OPEN',version=version+1,occurrences=occurrences+1,observed_order_no=VALUES(observed_order_no),observed_amount=VALUES(observed_amount),observed_time=VALUES(observed_time),last_seen_at=CURRENT_TIMESTAMP(3)",
                order.getPayOrderNo(),reason,order.getAmount(),response==null?null:response.getPayOrderNo(),response==null?null:response.getTotalAmount(),response==null?null:response.getSuccessTime());
    }
    private static long delay(int attempts) {return Math.min(300,1L<<Math.min(attempts,8));}
    private void finish(long number,String token,String state,String reason,long delay) {
        jdbc.update("UPDATE provider_payment_reconcile SET status=?,version=version+1,lease_token=NULL,last_error=?,next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL ? SECOND) WHERE pay_order_no=? AND lease_token=?",state,reason==null?null:reason.substring(0,Math.min(1000,reason.length())),delay,number,token);
    }
}
