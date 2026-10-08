package com.tianji.trade.service;

import com.tianji.trade.domain.po.RefundApply;
import com.tianji.trade.handler.FinancialReconciliationWorker;
import com.tianji.pay.sdk.client.PayClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class FinancialDueTimeTest {
    @Test void justAcceptedRefundIsDueWithoutWaitingForTheNextWholeSecond() {
        var data=new SingleConnectionDataSource("jdbc:mysql://127.0.0.1:"
                +System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")
                +"/mysql?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true",
                "root",System.getenv("ACCEPTANCE_DB_PASSWORD"),true);
        try {
            var jdbc=new JdbcTemplate(data);
            // Session-local frozen fractional time makes the precision bug deterministic.
            // Temporary tables belong only to this connection and shadow no live task data.
            jdbc.execute("SET timestamp=1791455000.500");
            jdbc.execute("CREATE TEMPORARY TABLE refund_delivery_task(refund_id BIGINT PRIMARY KEY,status VARCHAR(16) DEFAULT 'PENDING',next_attempt_at DATETIME(3),lease_token VARCHAR(64),attempts INT DEFAULT 0,last_error TEXT,version BIGINT DEFAULT 0)");
            jdbc.execute("CREATE TEMPORARY TABLE payment_reconcile(order_id BIGINT PRIMARY KEY,status VARCHAR(16),next_attempt_at DATETIME(3))");
            jdbc.update("INSERT INTO refund_delivery_task(refund_id,next_attempt_at) VALUES(41,CURRENT_TIMESTAMP(3))");
            var pending=new RefundApply();pending.setId(41L);pending.setStatus(3);
            var finished=new RefundApply();finished.setId(41L);finished.setStatus(4);
            var refunds=mock(IRefundApplyService.class);
            when(refunds.getById(41L)).thenReturn(pending,finished);
            var executor=new ThreadPoolTaskExecutor(){@Override public void execute(Runnable work){work.run();}};
            var worker=new FinancialReconciliationWorker(jdbc,refunds,mock(IOrderService.class),mock(PayClient.class),executor);
            worker.poll();
            verify(refunds).sendRefundRequest(pending);
            assertEquals("DONE",jdbc.queryForObject("SELECT status FROM refund_delivery_task WHERE refund_id=41",String.class));
        } finally {data.destroy();}
    }
}
