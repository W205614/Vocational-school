package com.tianji.promotion.strategy.discount;

import com.tianji.common.utils.NumberUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.promotion.domain.po.Coupon;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PerPriceDiscount implements Discount {

    private final static String RULE_TEMPLATE = "每满{}减{}，上限{}";

    @Override
    public boolean canUse(int totalAmount, Coupon coupon) {
        return coupon.getThresholdAmount()!=null && coupon.getThresholdAmount()>0 && totalAmount >= coupon.getThresholdAmount();
    }

    @Override
    public int calculateDiscount(int totalAmount, Coupon coupon) {
        if(coupon.getThresholdAmount()==null || coupon.getThresholdAmount()<=0 || coupon.getDiscountValue()==null || coupon.getDiscountValue()<0)
            throw new com.tianji.common.exceptions.BadRequestException("每满减券参数无效");
        long discount=(long)(totalAmount/coupon.getThresholdAmount())*coupon.getDiscountValue();
        int cap=coupon.getMaxDiscountAmount()==null || coupon.getMaxDiscountAmount()<=0?totalAmount:coupon.getMaxDiscountAmount();
        return (int)Math.min(totalAmount,Math.min(discount,cap));
    }

    @Override
    public String getRule(Coupon coupon) {
        return StringUtils.format(
                RULE_TEMPLATE,
                NumberUtils.scaleToStr(coupon.getThresholdAmount(), 2),
                NumberUtils.scaleToStr(coupon.getDiscountValue(), 2),
                NumberUtils.scaleToStr(coupon.getMaxDiscountAmount(), 2));
    }
}
