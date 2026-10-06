package com.tianji.trade.domain.vo;

import com.tianji.api.dto.promotion.CouponDiscountDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "订单确认页信息")
public class OrderConfirmVO {
    @Schema(description = "订单id")
    private Long orderId;
    @Schema(description = "订单总金额")
    private Integer totalAmount;
    @Schema(description = "优惠折扣方案")
    private List<CouponDiscountDTO> discounts;
    @Schema(description = "订单中包含的课程")
    private List<OrderCourseVO> courses;
}
