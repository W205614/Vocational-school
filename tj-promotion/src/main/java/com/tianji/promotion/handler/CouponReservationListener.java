package com.tianji.promotion.handler;
import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.autoconfigure.reliability.InboxStore;
import com.tianji.promotion.service.impl.CouponReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.Component;
import static com.tianji.common.constants.MqConstants.Exchange.*;
import static com.tianji.common.constants.MqConstants.Key.ORDER_PAY_KEY;
@Component @RequiredArgsConstructor
public class CouponReservationListener {
    private final InboxStore inbox;private final CouponReservationService reservations;
    public record Release(long orderId,long userId) {}
    @RabbitListener(bindings=@QueueBinding(value=@Queue(name="coupon.reservation.confirm.queue",durable="true"),exchange=@Exchange(name=ORDER_EXCHANGE,type=ExchangeTypes.TOPIC),key=ORDER_PAY_KEY))
    public void confirm(OrderBasicDTO order,Message raw) {inbox.once("coupon.confirm",raw.getMessageProperties().getMessageId(),()->reservations.transition(order.getOrderId(),order.getUserId(),true));}
    @RabbitListener(bindings=@QueueBinding(value=@Queue(name="coupon.reservation.release.queue",durable="true"),exchange=@Exchange(name=PROMOTION_EXCHANGE,type=ExchangeTypes.TOPIC),key="coupon.reservation.release"))
    public void release(Release release,Message raw) {inbox.once("coupon.release",raw.getMessageProperties().getMessageId(),()->reservations.transition(release.orderId(),release.userId(),false));}
}
