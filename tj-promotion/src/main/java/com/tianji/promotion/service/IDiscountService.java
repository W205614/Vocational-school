package com.tianji.promotion.service;

import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCouponDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;

import java.util.List;

public interface IDiscountService {
    com.tianji.api.dto.promotion.CouponQuoteDTO quote(List<OrderCourseDTO> courses, List<Long> couponIds);
    List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourses);

    CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO orderCouponDTO);
}
