package com.zhixuetang.promotion.service;

import com.zhixuetang.api.dto.promotion.CouponDiscountDTO;
import com.zhixuetang.api.dto.promotion.OrderCouponDTO;
import com.zhixuetang.api.dto.promotion.OrderCourseDTO;

import java.util.List;

public interface IDiscountService {
    com.zhixuetang.api.dto.promotion.CouponQuoteDTO quote(
            List<OrderCourseDTO> courses, List<Long> couponIds);

    List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourses);

    CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO orderCouponDTO);
}
