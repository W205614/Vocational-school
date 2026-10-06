package com.tianji.trade.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "支付申请信息")
@Data
public class PayApplyFormDTO {
    @Schema(description = "订单id",required = true)
    private Long orderId;
    @Schema(description = "支付渠道码，wxPay,aliPay",required = true)
    private String payChannelCode;
}