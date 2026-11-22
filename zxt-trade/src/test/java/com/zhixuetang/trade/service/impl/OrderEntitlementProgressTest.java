package com.zhixuetang.trade.service.impl;

import com.zhixuetang.api.dto.trade.OrderEntitlementLookup.Status;
import com.zhixuetang.trade.constants.OrderStatus;
import com.zhixuetang.trade.domain.vo.OrderEntitlementVO.State;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderEntitlementProgressTest {
    private static final LocalDateTime GRANTED = LocalDateTime.of(2026, 1, 2, 10, 0);

    @Test
    void distinguishesUnpaidMissingUnavailableAndExpiredEntitlements() {
        var waiting = new Status(8, null, null, false, false, false);
        assertEquals(State.NOT_GRANTED,
                OrderEntitlementProgress.view(OrderStatus.NO_PAY.getValue(), null).state());
        assertEquals(State.SYNC_PENDING,
                OrderEntitlementProgress.view(OrderStatus.PAYED.getValue(), waiting).state());
        assertEquals(State.CHECK_UNAVAILABLE,
                OrderEntitlementProgress.view(OrderStatus.PAYED.getValue(), null).state());
        var expired = new Status(8, GRANTED, GRANTED.plusMonths(1), true, false, false);
        var view = OrderEntitlementProgress.view(OrderStatus.PAYED.getValue(), expired);
        assertEquals(State.EXPIRED, view.state());
        assertFalse(view.canLearn());
        assertEquals(GRANTED.plusMonths(1), view.expiresAt());
    }

    @Test
    void distinguishesRefundProjectionFromLearningThroughAnotherOrder() {
        var stillProjected = new Status(8, GRANTED, null, true, true, false);
        var active = OrderEntitlementProgress.view(OrderStatus.PAYED.getValue(), stillProjected);
        assertEquals(State.ACTIVE, active.state());
        assertTrue(active.canLearn());
        var pendingRevocation = OrderEntitlementProgress.view(OrderStatus.REFUND_FINISHED.getValue(), stillProjected);
        assertEquals(State.REVOCATION_PENDING, pendingRevocation.state());
        assertFalse(pendingRevocation.canLearn());
        var anotherOrder = new Status(8, GRANTED, null, false, false, true);
        var revoked = OrderEntitlementProgress.view(OrderStatus.REFUND_FINISHED.getValue(), anotherOrder);
        assertEquals(State.REVOKED, revoked.state());
        assertTrue(revoked.otherOrderActive());
        assertTrue(revoked.canLearn());
    }
}
