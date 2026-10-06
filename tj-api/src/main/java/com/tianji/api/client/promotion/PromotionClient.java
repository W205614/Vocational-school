package com.tianji.api.client.promotion;

import com.tianji.api.client.promotion.fallback.PromotionClientFallback;
import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCouponDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(value = "promotion-service", fallbackFactory = PromotionClientFallback.class)
public interface PromotionClient {

    @PostMapping("/user-coupons/available")
    List<CouponDiscountDTO> findDiscountSolution(@RequestBody List<OrderCourseDTO> orderCourses);

    @Operation(summary = "根据券方案计算订单优惠明细")
    @PostMapping("/user-coupons/discount")
    CouponDiscountDTO queryDiscountDetailByOrder(@RequestBody OrderCouponDTO orderCouponDTO);

    @Operation(summary = "核销指定优惠券")
    @PutMapping("/user-coupons/use")
    void writeOffCoupon(@Parameter(description = "用户优惠券id集合") @RequestParam("couponIds") List<Long> userCouponIds);

    @Operation(summary = "退还指定优惠券")
    @PutMapping("/user-coupons/refund")
    void refundCoupon(@Parameter(description = "用户优惠券id集合") @RequestParam("couponIds") List<Long> userCouponIds);

    @Operation(summary = "分页查询我的优惠券接口")
    @GetMapping("/user-coupons/rules")
    List<String> queryDiscountRules(@Parameter(description = "用户优惠券id集合") @RequestParam("couponIds") List<Long> userCouponIds);

    @PutMapping("/internal/v2/coupon-reservations/{order}")
    java.util.Map<String,Object> reserveCoupons(@PathVariable("order") Long order,@RequestBody com.tianji.api.dto.promotion.CouponReservationDTO request);
}
