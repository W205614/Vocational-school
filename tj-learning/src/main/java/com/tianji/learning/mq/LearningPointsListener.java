package com.tianji.learning.mq;

import com.tianji.common.constants.MqConstants;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.IPointsRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LearningPointsListener {

    private final com.tianji.learning.service.impl.PointsRecordServiceImpl recordService;
    private final tools.jackson.databind.json.JsonMapper json;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "qa.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.WRITE_REPLY
    ))
    public void listenWriteReplyMessage(Long userId, org.springframework.amqp.core.Message raw) {
        // Legacy reply.new events never imply teacher acceptance and cannot award points.
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "sign.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.SIGN_IN
    ))
    public void listenSignInMessage(SignInMessage message, org.springframework.amqp.core.Message raw) {
        java.time.LocalDateTime occurred=message.getOccurredAt();
        if(occurred==null) {
            var envelope=json.readTree(raw.getBody());String key=envelope.has("businessKey")?envelope.get("businessKey").asString():"";
            String prefix="sign:"+message.getUserId()+":";
            if(!key.startsWith(prefix))throw new IllegalArgumentException("旧签到事件缺少可核实日期，需要人工核对");
            occurred=java.time.LocalDate.parse(key.substring(prefix.length())).atStartOfDay();
        }
        recordService.award(message.getUserId(),message.getPoints(),PointsRecordType.SIGN,raw.getMessageProperties().getMessageId(),occurred);
    }
}
