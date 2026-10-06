package com.tianji.message.handler;

import com.tianji.api.dto.sms.SmsInfoDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.message.service.ISmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmsMessageHandler {

    private final com.tianji.message.service.impl.SmsDeliveryTasks deliveries;
    private final com.tianji.common.autoconfigure.reliability.InboxStore inbox;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "sms.message.queue", durable = "true"),
            exchange = @Exchange(MqConstants.Exchange.SMS_EXCHANGE),
            key = MqConstants.Key.SMS_MESSAGE
    ))
    public void listenSmsMessage(SmsInfoDTO smsInfoDTO,org.springframework.amqp.core.Message raw){
        String event=raw.getMessageProperties().getMessageId();
        inbox.once("sms.stage",event,()->deliveries.stage(java.util.UUID.nameUUIDFromBytes(event.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),smsInfoDTO));
    }
}
