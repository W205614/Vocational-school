package com.tianji.api.client.promotion.fallback;

import com.tianji.api.client.promotion.PromotionClient;
import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCouponDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.common.exceptions.BizIllegalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.Collections;
import java.util.List;

@Slf4j
public class PromotionClientFallback implements FallbackFactory<PromotionClient> {
    @Override
    public PromotionClient create(Throwable cause) {
        log.error("查询促销服务出现异常, ", cause);
        return new PromotionClient() {
            @Override public java.util.Map<String,Object> reserveCoupons(Long order,com.tianji.api.dto.promotion.CouponReservationDTO request) {
                throw new com.tianji.common.exceptions.CommonException("优惠券预占暂不可用",cause);
            }
            @Override
            public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourses) {
                throw new com.tianji.common.exceptions.CommonException("促销服务暂不可用",cause);
            }

            @Override
            public CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO orderCouponDTO) {
                throw new com.tianji.common.exceptions.CommonException("促销服务暂不可用",cause);
            }

            @Override
            public void writeOffCoupon(List<Long> userCouponIds) {
                throw new BizIllegalException(500, "核销优惠券异常", cause);
            }

            @Override
            public void refundCoupon(List<Long> userCouponIds) {
                throw new BizIllegalException(500, "退还优惠券异常", cause);
            }

            @Override
            public List<String> queryDiscountRules(List<Long> userCouponIds) {
                throw new com.tianji.common.exceptions.CommonException("促销服务暂不可用",cause);
            }
        };
    }
}
