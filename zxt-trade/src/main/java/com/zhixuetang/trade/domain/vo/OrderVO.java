package com.zhixuetang.trade.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "订单详细信息")
public class OrderVO {
    @Schema(description = "订单id")
    private Long id;

    @Schema(description = "订单创建时间")
    private LocalDateTime createTime;

    @Schema(description = "已确认的付款时间，包括关闭订单后确认的支付事实")
    private LocalDateTime paidAt;

    @Schema(description = "付款已确认但订单已关闭，需核对处理")
    private Boolean paymentNeedsReview;

    @Schema(description = "订单实付金额")
    private Integer realAmount;

    @Schema(description = "支付渠道，ZERO表示优惠抵扣后的零元成交")
    private String payChannel;

    @Schema(description = "支付流水；零元成交没有外部支付流水")
    private Long payOrderNo;

    @Schema(description = "订单明细金额")
    private Integer totalAmount;

    @Schema(description = "优惠券规则,可以有多个优惠券规则")
    private String couponDesc;

    @Schema(description = "优惠总金额")
    private Double discountAmount;

    @Schema(description = "订单状态，1：待支付，2：已支付，3：已关闭，4：已完成，5：已报名，6：已申请退款")
    private Integer status;

    @Schema(description = "订单状态描述")
    private String statusDesc;

    @Schema(description = "订单状态描述")
    private String message;

    @Schema(description = "订单进度明细")
    private List<OrderProgressNodeVO> progressNodes;

    @Schema(description = "订单中课程明细")
    private List<OrderDetailVO> details;
}
