package com.zhixuetang.api.dto.trade;

import java.time.LocalDateTime;

public final class OrderEntitlementLookup {
    private OrderEntitlementLookup() {}

    public record Line(long detailId, long userId, long courseId) {}

    public record Status(long detailId, LocalDateTime recordedAt, LocalDateTime expiresAt,
                         boolean active, boolean unexpired, boolean otherActive) {}
}
