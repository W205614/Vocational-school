package com.tianji.api.dto.promotion;

import lombok.Data;
import java.util.*;

/** Bounded coupon search, independent of how many tickets the owner holds. */
@Data
public class CouponQuoteDTO {
    private List<CouponDiscountDTO> discounts = List.of();
    private List<Map<String,Object>> availableCoupons = List.of();
    private boolean couponSelectionRequired;
    private String discountError;
}
