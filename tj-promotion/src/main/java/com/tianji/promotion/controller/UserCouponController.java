package com.tianji.promotion.controller;


import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCouponDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.service.IDiscountService;
import com.tianji.promotion.service.IUserCouponService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-13
 */
@RestController
@RequestMapping("/user-coupons")
@RequiredArgsConstructor
@Tag(name = "优惠卷相关接口")
public class UserCouponController {

    private final IUserCouponService userCouponService;

    private final IDiscountService discountService;
    private final com.tianji.common.autoconfigure.reliability.OperationStore operations;

    @Operation(summary = "领取优惠卷接口")
    @PostMapping("/{couponId}/receive")
    public org.springframework.http.ResponseEntity<?> receiveCoupon(@PathVariable("couponId") Long couponId,@RequestHeader("Idempotency-Key") String key) {
        return org.springframework.http.ResponseEntity.accepted().body(operations.submit(com.tianji.common.utils.UserContext.requireUser(),"COUPON_CLAIM",key,new com.tianji.promotion.service.impl.CouponClaimService.Request(couponId,null)));
    }

    @Operation(summary = "兑换码兑换优惠券接口")
    @PostMapping("/{code}/exchange")
    public org.springframework.http.ResponseEntity<?> exchangeCoupon(@PathVariable("code") String code,@RequestHeader("Idempotency-Key") String key) {
        return org.springframework.http.ResponseEntity.accepted().body(operations.submit(com.tianji.common.utils.UserContext.requireUser(),"COUPON_CLAIM",key,new com.tianji.promotion.service.impl.CouponClaimService.Request(null,code)));
    }

    @Operation(summary = "分页查询我的优惠券")
    @GetMapping("/page")
    public PageDTO<CouponVO> queryMyCouponPage(UserCouponQuery query) {
        return userCouponService.queryMyCouponPage(query);
    }

    @Operation(summary = "查询我的优惠券可用方案")
    @PostMapping("/available")
    public List<CouponDiscountDTO> findDiscountSolution(@RequestBody List<OrderCourseDTO> orderCourses) {
        return discountService.findDiscountSolution(orderCourses);
    }

    @Operation(summary = "根据券方案计算订单优惠明细")
    @PostMapping("/discount")
    public CouponDiscountDTO queryDiscountDetailByOrder(@RequestBody OrderCouponDTO orderCouponDTO){
        return discountService.queryDiscountDetailByOrder(orderCouponDTO);
    }

    @Operation(summary = "核销指定优惠券")
    @PutMapping("/use")
    public void writeOffCoupon(@Parameter(description = "用户优惠券id集合") @RequestParam("couponIds") List<Long> userCouponIds){
        com.tianji.common.utils.InternalAuth.requireService();
        userCouponService.writeOffCoupon(userCouponIds);
    }

    @Operation(summary = "退还指定优惠券")
    @PutMapping("/refund")
    public void refundCoupon(@Parameter(description = "用户优惠券id集合") @RequestParam("couponIds") List<Long> userCouponIds){
        com.tianji.common.utils.InternalAuth.requireService();
        userCouponService.refundCoupon(userCouponIds);
    }

    @Operation(summary = "分页查询我的优惠券接口")
    @GetMapping("/rules")
    public List<String> queryDiscountRules(
            @Parameter(description = "用户优惠券id集合") @RequestParam("couponIds") List<Long> userCouponIds){
        return userCouponService.queryDiscountRules(userCouponIds);
    }
}
