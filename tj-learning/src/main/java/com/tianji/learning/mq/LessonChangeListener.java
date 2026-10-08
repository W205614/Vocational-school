package com.tianji.learning.mq;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.autoconfigure.reliability.InboxStore;
import com.tianji.learning.service.impl.LearningEntitlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.Component;
import static com.tianji.common.constants.MqConstants.Exchange.ORDER_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.*;
@Component @RequiredArgsConstructor
public class LessonChangeListener {
    private final LearningEntitlementService entitlements;private final InboxStore inbox;
    private final com.tianji.api.client.trade.TradeClient trade;
    @RabbitListener(bindings=@QueueBinding(value=@Queue(name="learning.lesson.pay.queue",durable="true"),
            exchange=@Exchange(name=ORDER_EXCHANGE,type=ExchangeTypes.TOPIC),key=ORDER_PAY_KEY))
    public void listenLessonPay(OrderBasicDTO order,Message raw) {
        if(order==null || order.getOrderId()==null || order.getDetailIds()==null || order.getUserId()==null || order.getCourseIds()==null || order.getCourseIds().isEmpty()) throw new IllegalArgumentException("Invalid grant event");
        // An already paid order remains authoritative when catalog metadata changes
        // or is withdrawn before this durable event is consumed.
        var metadata=order.getCourseIds().stream().map(id->{var course=new CourseSimpleInfoDTO();course.setId(id);return course;}).toList();
        java.util.List<Long> revoked=new java.util.ArrayList<>();
        if(order.getValidDurations()==null) {
            var facts=trade.orderEntitlements(order.getOrderId());
            var durations=new java.util.HashMap<Long,Integer>();
            for(var fact:facts) if(order.getCourseIds().contains(fact.courseId())) {
                if(fact.orderId()!=order.getOrderId() || fact.userId()!=order.getUserId() || !java.util.Objects.equals(order.getDetailIds().get(fact.courseId()),fact.detailId())) throw new IllegalArgumentException("Legacy grant provenance mismatch");
                durations.put(fact.courseId(),fact.validDuration());order.setFinishTime(fact.purchasedAt());
                if(fact.revoked()) revoked.add(fact.courseId());
            }
            if(durations.size()!=order.getCourseIds().size()) throw new IllegalArgumentException("Missing legacy purchased facts");
            order.setValidDurations(durations);
        }
        inbox.once("lesson.grant",raw.getMessageProperties().getMessageId(),()->{
            entitlements.grant(order,metadata);
            if(!revoked.isEmpty()) entitlements.revoke(OrderBasicDTO.builder().orderId(order.getOrderId()).userId(order.getUserId()).courseIds(revoked).detailIds(order.getDetailIds()).build());
        });
    }
    @RabbitListener(bindings=@QueueBinding(value=@Queue(name="learning.lesson.Refund.queue",durable="true"),
            exchange=@Exchange(name=ORDER_EXCHANGE,type=ExchangeTypes.TOPIC),key=ORDER_REFUND_KEY))
    public void listenLessonRefund(OrderBasicDTO order,Message raw) {
        if(order==null || order.getUserId()==null || order.getCourseIds()==null) throw new IllegalArgumentException("Invalid refund event");
        inbox.once("lesson.refund",raw.getMessageProperties().getMessageId(),()->entitlements.revoke(order));
    }
}
