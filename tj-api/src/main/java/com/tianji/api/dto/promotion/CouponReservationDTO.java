package com.tianji.api.dto.promotion;
import java.time.LocalDateTime;
import java.util.List;
public record CouponReservationDTO(Long userId,List<Long> couponIds,LocalDateTime expiresAt) {}
