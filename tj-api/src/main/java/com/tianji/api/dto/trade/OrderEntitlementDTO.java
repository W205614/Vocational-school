package com.tianji.api.dto.trade;
import java.time.LocalDateTime;
/** Retained financial facts used to reconcile legacy events without current catalogue values. */
public record OrderEntitlementDTO(long orderId,long detailId,long userId,long courseId,int validDuration,LocalDateTime purchasedAt,boolean revoked) {}
