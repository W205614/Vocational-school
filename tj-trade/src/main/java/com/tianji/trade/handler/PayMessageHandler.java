package com.tianji.trade.handler;

import com.tianji.common.constants.MqConstants;
import com.tianji.pay.sdk.dto.PayResultDTO;
import com.tianji.pay.sdk.dto.RefundResultDTO;
import com.tianji.trade.domain.dto.OrderDelayQueryDTO;
import com.tianji.trade.service.IOrderService;
import com.tianji.trade.service.IPayService;
import com.tianji.trade.service.IRefundApplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayMessageHandler {

    private final IOrderService orderService;
    private final IRefundApplyService refundApplyService;
    private final IPayService payService;
    private final com.tianji.common.autoconfigure.reliability.InboxStore inbox;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "trade.pay.success.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.PAY_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.PAY_SUCCESS
    ))
    public void listenPaySuccess(PayResultDTO payResult,org.springframework.amqp.core.Message raw){
        log.debug("收到支付成功通知：{}", payResult);
        inbox.once("trade.payment",raw.getMessageProperties().getMessageId(),()->orderService.handlePaySuccess(payResult));
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "trade.refund.result.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.PAY_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.REFUND_CHANGE
    ))
    public void listenRefundResult(RefundResultDTO refundResult,org.springframework.amqp.core.Message raw){
        log.debug("收到退款变更成功通知：{}", refundResult);
        inbox.once("trade.refund",raw.getMessageProperties().getMessageId(),()->refundApplyService.handleRefundResult(refundResult));
    }

}
