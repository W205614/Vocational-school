package com.tianji.learning.mq;
import com.tianji.api.dto.remark.LikeTimesDTO;
import com.tianji.common.autoconfigure.reliability.InboxStore;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.QA_LIKED_TIMES_KEY;
@Component @RequiredArgsConstructor
public class LikeTimesChangeListener {
    private final JdbcTemplate jdbc; private final InboxStore inbox;
    @RabbitListener(bindings=@QueueBinding(value=@Queue(name="qa.liked.times.queue",durable="true"),
            exchange=@Exchange(name=LIKE_RECORD_EXCHANGE,type=ExchangeTypes.TOPIC),key=QA_LIKED_TIMES_KEY))
    public void listenReplyLikedTimesChange(List<LikeTimesDTO> updates,Message raw) {
        inbox.once("reply.likes",raw.getMessageProperties().getMessageId(),() -> {
            for(LikeTimesDTO dto:updates) {
                if(dto.getVersion()==null || dto.getLikeTimes()<0) throw new IllegalArgumentException("Missing projection version");
                jdbc.update("UPDATE interaction_reply SET liked_times=?,liked_version=? WHERE id=? AND liked_version<?",dto.getLikeTimes(),dto.getVersion(),dto.getBizId(),dto.getVersion());
            }
        });
    }
}
