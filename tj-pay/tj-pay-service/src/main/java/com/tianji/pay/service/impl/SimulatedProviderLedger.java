package com.tianji.pay.service.impl;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.JdbcTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;

/** An independent provider ledger commit survives a lost business notification. */
@Service @ConditionalOnProperty(name="tj.pay.simulated",havingValue="true")
public class SimulatedProviderLedger {
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final Long dropBusiness;
    public record Payment(long number,int amount,LocalDateTime time,boolean notificationDropped) {}
    public SimulatedProviderLedger(JdbcTemplate jdbc,PlatformTransactionManager manager,Environment environment) {
        this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        dropBusiness=environment.getProperty("tj.acceptance.simulated-payment.drop-notification-business",Long.class);
        if(dropBusiness!=null && (dropBusiness<=0 || Arrays.stream(environment.getActiveProfiles()).noneMatch("acceptance"::equals)))
            throw new IllegalStateException("Lost notification injection requires acceptance profile and an exact business order");
    }
    public Payment confirm(long business,long user) {
        return tx.execute(status->{
            var rows=jdbc.queryForList("SELECT pay_order_no,amount FROM pay_order WHERE biz_order_no=? AND biz_user_id=? AND pay_channel_code='mockPay' FOR UPDATE",business,user);
            if(rows.isEmpty())throw new BadRequestException("模拟支付单不存在");
            var row=rows.getFirst();long number=((Number)row.get("pay_order_no")).longValue();int amount=((Number)row.get("amount")).intValue();
            if(jdbc.update("UPDATE simulated_provider_payment SET status=3,success_time=COALESCE(success_time,NOW(3)) WHERE pay_order_no=? AND amount=?",number,amount)!=1)
                throw new BadRequestException("尚未创建匹配的模拟支付请求");
            LocalDateTime time=JdbcTime.localDateTime(jdbc.queryForObject("SELECT success_time FROM simulated_provider_payment WHERE pay_order_no=?",Object.class,number));
            return new Payment(number,amount,time,Objects.equals(dropBusiness,business));
        });
    }
}
