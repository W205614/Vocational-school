package com.tianji.search.mq;
import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.autoconfigure.reliability.InboxStore;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.tianji.common.constants.MqConstants.Exchange.ORDER_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.*;
@Component @RequiredArgsConstructor
public class OrderEventListener {
    private final JdbcTemplate jdbc;private final InboxStore inbox;
    @RabbitListener(bindings=@QueueBinding(value=@Queue(name="search.order.pay.queue",durable="true"),exchange=@Exchange(name=ORDER_EXCHANGE,type=ExchangeTypes.TOPIC),key=ORDER_PAY_KEY))
    public void listenOrderPay(OrderBasicDTO order,Message raw) {change(order,raw,true);}
    @RabbitListener(bindings=@QueueBinding(value=@Queue(name="search.order.refund.queue",durable="true"),exchange=@Exchange(name=ORDER_EXCHANGE,type=ExchangeTypes.TOPIC),key=ORDER_REFUND_KEY))
    public void listenOrderRefund(OrderBasicDTO order,Message raw) {change(order,raw,false);}
    private void change(OrderBasicDTO order,Message raw,boolean active) {
        if(order==null || order.getDetailIds()==null || order.getCourseIds()==null) throw new IllegalArgumentException("Missing sale provenance");
        inbox.once("search.sales."+active,raw.getMessageProperties().getMessageId(),() -> {
            for(Long course:order.getCourseIds().stream().sorted().toList()) {
                Long detail=order.getDetailIds().get(course);if(detail==null) throw new IllegalArgumentException("Missing order detail");
                jdbc.update("INSERT INTO course_sales_projection(course_id,sold,version,processed_version) VALUES(?,0,0,0) ON DUPLICATE KEY UPDATE course_id=VALUES(course_id)",course);
                jdbc.queryForMap("SELECT version FROM course_sales_projection WHERE course_id=? FOR UPDATE",course);
                if(active) jdbc.update("INSERT IGNORE INTO course_sale_detail(order_detail_id,course_id,active) VALUES(?,?,1)",detail,course);
                else jdbc.update("INSERT INTO course_sale_detail(order_detail_id,course_id,active) VALUES(?,?,0) ON DUPLICATE KEY UPDATE active=0",detail,course);
                jdbc.update("UPDATE course_sales_projection SET sold=(SELECT COUNT(*) FROM course_sale_detail WHERE course_id=? AND active=1),version=version+1,status='PENDING',attempts=0,next_attempt_at=NOW(3) WHERE course_id=?",course,course);
            }
        });
    }
}
