package com.tianji.trade.handler;
import com.tianji.common.autoconfigure.reliability.OutboxStore;
import com.tianji.common.constants.MqConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;
@Component @RequiredArgsConstructor
public class OrderTimeoutWorker {
    private final JdbcTemplate jdbc;private final PlatformTransactionManager manager;private final OutboxStore outbox;
    @Scheduled(fixedDelayString="${tj.trade.timeout-interval-ms:1000}") public void expire() {
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            for(var row:jdbc.queryForList("SELECT * FROM order_creation WHERE status IN('ACTIVE','CREATED') AND expires_at<=NOW() ORDER BY expires_at LIMIT 50 FOR UPDATE SKIP LOCKED")) {
                Object order=row.get("order_id");Object user=row.get("user_id");
                var states=jdbc.queryForList("SELECT status FROM `order` WHERE id=? FOR UPDATE",order);
                if(!states.isEmpty() && ((Number)states.getFirst().get("status")).intValue()!=1) {
                    jdbc.update("UPDATE order_creation SET status='FINAL' WHERE order_id=?",order);continue;
                }
                jdbc.update("UPDATE `order` SET status=3,message='支付超时',close_time=NOW() WHERE id=? AND status=1",order);
                jdbc.update("UPDATE order_detail SET status=3 WHERE order_id=? AND status=1",order);
                jdbc.update("UPDATE order_creation SET status='EXPIRED' WHERE order_id=?",order);
                outbox.enqueue("order:"+order+":timeout-release",MqConstants.Exchange.PROMOTION_EXCHANGE,"coupon.reservation.release",Map.of("orderId",order,"userId",user));
            }
        });
    }
}
