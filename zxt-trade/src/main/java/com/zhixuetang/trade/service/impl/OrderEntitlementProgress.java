package com.zhixuetang.trade.service.impl;

import com.zhixuetang.api.client.learning.OrderEntitlementLookupClient;
import com.zhixuetang.api.dto.trade.OrderEntitlementLookup.Line;
import com.zhixuetang.api.dto.trade.OrderEntitlementLookup.Status;
import com.zhixuetang.trade.constants.OrderStatus;
import com.zhixuetang.trade.domain.vo.OrderEntitlementVO;
import com.zhixuetang.trade.domain.vo.OrderEntitlementVO.State;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderEntitlementProgress {
    private final OrderEntitlementLookupClient learning;

    public Map<Long, Status> lookup(List<Line> lines) {
        if (lines.isEmpty()) return Map.of();
        return learning.orderEntitlements(lines).stream()
                .collect(Collectors.toUnmodifiableMap(Status::detailId, Function.identity()));
    }

    static OrderEntitlementVO view(Integer orderLineStatus, Status status) {
        if (OrderStatus.NO_PAY.equalsValue(orderLineStatus)
                || OrderStatus.CLOSED.equalsValue(orderLineStatus))
            return new OrderEntitlementVO(State.NOT_GRANTED, false, false, null, null);
        if (status == null)
            return new OrderEntitlementVO(State.CHECK_UNAVAILABLE, false, false, null, null);

        boolean refunded = OrderStatus.REFUND_FINISHED.equalsValue(orderLineStatus);
        State state;
        if (refunded) state = status.active() ? State.REVOCATION_PENDING : State.REVOKED;
        else if (status.recordedAt() == null) state = State.SYNC_PENDING;
        else if (!status.active()) state = State.REVOKED;
        else state = status.unexpired() ? State.ACTIVE : State.EXPIRED;
        return new OrderEntitlementVO(state, state == State.ACTIVE || status.otherActive(),
                status.otherActive(), status.active() ? status.recordedAt() : null, status.expiresAt());
    }
}
