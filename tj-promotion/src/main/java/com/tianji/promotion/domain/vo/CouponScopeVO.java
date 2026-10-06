package com.tianji.promotion.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Schema(description = "优惠券使用范围")
@NoArgsConstructor
@AllArgsConstructor
public class CouponScopeVO {
    @Schema(description = "范围id集合")
    private Long id;
    @Schema(description = "范围名称集合")
    private String name;
}
