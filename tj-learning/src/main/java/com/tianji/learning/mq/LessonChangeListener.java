package com.tianji.learning.mq;
import com.tianji.api.client.course.CourseClient;
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
    private final CourseClient courses;private final LearningEntitlementService entitlements;private final InboxStore inbox;
    @RabbitListener(bindings=@QueueBinding(value=@Queue(name="learning.lesson.pay.queue",durable="true"),
            exchange=@Exchange(name=ORDER_EXCHANGE,type=ExchangeTypes.TOPIC),key=ORDER_PAY_KEY))
    public void listenLessonPay(OrderBasicDTO order,Message raw) {
        if(order==null || order.getUserId()==null || order.getCourseIds()==null || order.getCourseIds().isEmpty()) throw new IllegalArgumentException("Invalid grant event");
        var metadata=courses.getSimpleInfoList(order.getCourseIds());
        inbox.once("lesson.grant",raw.getMessageProperties().getMessageId(),()->entitlements.grant(order,metadata));
    }
    @RabbitListener(bindings=@QueueBinding(value=@Queue(name="learning.lesson.Refund.queue",durable="true"),
            exchange=@Exchange(name=ORDER_EXCHANGE,type=ExchangeTypes.TOPIC),key=ORDER_REFUND_KEY))
    public void listenLessonRefund(OrderBasicDTO order,Message raw) {
        if(order==null || order.getUserId()==null || order.getCourseIds()==null) throw new IllegalArgumentException("Invalid refund event");
        inbox.once("lesson.refund",raw.getMessageProperties().getMessageId(),()->entitlements.revoke(order));
    }
}
