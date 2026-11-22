package com.zhixuetang.trade.domain.vo;

import java.time.LocalDateTime;

public record OrderEntitlementVO(State state, boolean canLearn, boolean otherOrderActive,
                                 LocalDateTime recordedAt, LocalDateTime expiresAt) {
    public enum State {
        NOT_GRANTED, SYNC_PENDING, ACTIVE, EXPIRED, REVOCATION_PENDING, REVOKED, CHECK_UNAVAILABLE
    }
}
