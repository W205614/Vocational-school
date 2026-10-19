package com.tianji.trade.service.impl;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.autoconfigure.reliability.OutboxStore;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** A zero balance is a local purchase, never a fabricated provider payment fact. */
@Service @RequiredArgsConstructor
public class ZeroAmountSettlementService {
    private final JdbcTemplate jdbc;
    private final OutboxStore outbox;

    @Transactional public void settle(long id) {
        var order=jdbc.queryForMap("SELECT user_id,status,real_amount,pay_channel FROM `order` WHERE id=? FOR UPDATE",id);
        if(((Number)order.get("real_amount")).intValue()!=0) throw new ConflictException("订单仍需付款");
        if(((Number)order.get("status")).intValue()!=1) {
            if("ZERO".equals(order.get("pay_channel"))) return;
            throw new ConflictException("订单状态不允许零元成交");
        }
        LocalDateTime time=LocalDateTime.now();
        jdbc.update("UPDATE `order` SET status=2,pay_channel='ZERO',pay_time=?,message='优惠抵扣，零元成交' WHERE id=? AND status=1",time,id);
        jdbc.update("UPDATE order_detail SET status=2,pay_channel='ZERO',course_expire_time=CASE WHEN valid_duration IS NULL OR valid_duration<=0 THEN NULL ELSE DATE_ADD(?,INTERVAL valid_duration MONTH) END WHERE order_id=? AND status=1",time,id);
        var details=jdbc.queryForList("SELECT id,course_id,valid_duration FROM order_detail WHERE order_id=? ORDER BY id",id);
        if(details.isEmpty()) throw new ConflictException("订单条目不存在");
        Map<Long,Long> detailIds=new LinkedHashMap<>();Map<Long,Integer> durations=new LinkedHashMap<>();
        for(var detail:details) {
            long course=((Number)detail.get("course_id")).longValue();
            detailIds.put(course,((Number)detail.get("id")).longValue());
            durations.put(course,detail.get("valid_duration")==null?0:((Number)detail.get("valid_duration")).intValue());
        }
        // The same event grants entitlement and confirms coupon reservations through existing inboxes.
        outbox.enqueue("order:"+id+":paid",MqConstants.Exchange.ORDER_EXCHANGE,MqConstants.Key.ORDER_PAY_KEY,
                OrderBasicDTO.builder().orderId(id).userId(((Number)order.get("user_id")).longValue())
                        .courseIds(new ArrayList<>(detailIds.keySet())).detailIds(detailIds).validDurations(durations).finishTime(time).build());
    }
}
