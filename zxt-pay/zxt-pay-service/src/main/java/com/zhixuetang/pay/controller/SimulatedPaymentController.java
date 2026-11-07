package com.zhixuetang.pay.controller;

import com.zhixuetang.pay.service.impl.ProviderSettlementService;
import com.zhixuetang.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxt.pay.simulated", havingValue = "true")
public class SimulatedPaymentController {
    private final com.zhixuetang.pay.service.impl.SimulatedProviderLedger ledger;
    private final ProviderSettlementService settlement;

    @PostMapping("/api/v2/simulator/payments/{business}/confirm")
    public Object confirm(@PathVariable long business) {
        var payment = ledger.confirm(business, UserContext.requireUser());
        if (!payment.notificationDropped())
            settlement.paid(payment.number(), payment.amount(), payment.time());
        return Map.of("status", "PAID", "mode", "SIMULATED");
    }
}
