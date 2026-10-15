package com.tianji.learning.mq;

import com.tianji.common.autoconfigure.reliability.InboxStore;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.Objects;

@Component @RequiredArgsConstructor
public class LearningRewardListener {
    private final JdbcTemplate jdbc;private final InboxStore inbox;
    public record Fulfilled(String requestId,long userCouponId) {}
    public record Failed(String requestId,String errorCode,String errorMessage) {}
    @RabbitListener(bindings=@QueueBinding(value=@Queue(name="learning.reward.fulfilled.queue",durable="true"),exchange=@Exchange(name="learning.topic",type="topic"),key="learning.reward.fulfilled"))
    public void fulfilled(Fulfilled event,Message raw){
        if(event.requestId()==null || event.userCouponId()<1)throw new IllegalArgumentException("奖励回执无效");
        inbox.once("learning.reward.fulfilled",raw.getMessageProperties().getMessageId(),()->jdbc.update("UPDATE learning_reward_request SET status='FULFILLED',user_coupon_id=?,last_error=NULL,updated_at=CURRENT_TIMESTAMP(3) WHERE request_id=? AND status<>'FULFILLED'",event.userCouponId(),event.requestId()));
    }
    @RabbitListener(bindings=@QueueBinding(value=@Queue(name="learning.reward.failed.queue",durable="true"),exchange=@Exchange(name="learning.topic",type="topic"),key="learning.reward.failed"))
    public void failed(Failed event,Message raw){
        if(event.requestId()==null)throw new IllegalArgumentException("奖励回执无效");
        String detail=Objects.toString(event.errorCode(),"FAILED")+": "+Objects.toString(event.errorMessage(),"奖励发放失败");
        inbox.once("learning.reward.failed",raw.getMessageProperties().getMessageId(),()->jdbc.update("UPDATE learning_reward_request SET status='FAILED',last_error=?,updated_at=CURRENT_TIMESTAMP(3) WHERE request_id=? AND status<>'FULFILLED'",detail.substring(0,Math.min(500,detail.length())),event.requestId()));
    }
}
