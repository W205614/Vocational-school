package com.tianji.api.dto.trade;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class OrderBasicDTO {
    /**
     * 订单id
     */
    private Long orderId;
    /**
     * 下单用户id
     */
    private Long userId;
    /**
     * 下单的课程id集合
     */
    private List<Long> courseIds;
    /**
     * 订单完成时间
     */
    private LocalDateTime finishTime;
    private java.util.Map<Long,Long> detailIds;
    /** Purchased validity, not the course's current catalogue setting; zero means permanent. */
    private java.util.Map<Long,Integer> validDurations;
}
