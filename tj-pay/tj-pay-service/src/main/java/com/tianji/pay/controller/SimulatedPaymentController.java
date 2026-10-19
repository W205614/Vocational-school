package com.tianji.pay.controller;
import com.tianji.pay.service.impl.ProviderSettlementService;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor @ConditionalOnProperty(name="tj.pay.simulated",havingValue="true")
public class SimulatedPaymentController {
 private final com.tianji.pay.service.impl.SimulatedProviderLedger ledger;private final ProviderSettlementService settlement;
 @PostMapping("/api/v2/simulator/payments/{business}/confirm") public Object confirm(@PathVariable long business){
  var payment=ledger.confirm(business,UserContext.requireUser());
  if(!payment.notificationDropped())settlement.paid(payment.number(),payment.amount(),payment.time());
  return Map.of("status","PAID","mode","SIMULATED");
 }
}
