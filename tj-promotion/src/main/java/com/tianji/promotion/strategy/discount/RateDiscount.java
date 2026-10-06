package com.tianji.promotion.strategy.discount;

import com.tianji.common.utils.NumberUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.promotion.domain.po.Coupon;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RateDiscount implements Discount {

    private static final String RULE_TEMPLATE = "满{}打{}折，上限{}元";

    @Override
    public boolean canUse(int totalAmount, Coupon coupon) {
        return totalAmount >= coupon.getThresholdAmount();
    }

    @Override
    public int calculateDiscount(int totalAmount,  Coupon coupon) {
        // 计算折扣，扩大100倍计算，向下取整，单位是分
        if(coupon.getDiscountValue()==null || coupon.getDiscountValue()<1 || coupon.getDiscountValue()>100)
            throw new com.tianji.common.exceptions.BadRequestException("折扣比例无效");
        int cap=coupon.getMaxDiscountAmount()==null || coupon.getMaxDiscountAmount()<=0?totalAmount:coupon.getMaxDiscountAmount();
        return (int)Math.min(cap,(long)totalAmount*(100-coupon.getDiscountValue())/100);
    }

    @Override
    public String getRule( Coupon coupon) {
        return StringUtils.format(
                RULE_TEMPLATE,
                NumberUtils.scaleToStr(coupon.getThresholdAmount(), 2),
                NumberUtils.scaleToStr(coupon.getDiscountValue(), 1),
                NumberUtils.scaleToStr(coupon.getMaxDiscountAmount(), 2)
        );
    }
}
