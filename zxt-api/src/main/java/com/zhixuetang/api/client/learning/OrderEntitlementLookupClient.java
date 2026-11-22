package com.zhixuetang.api.client.learning;

import com.zhixuetang.api.dto.trade.OrderEntitlementLookup.Line;
import com.zhixuetang.api.dto.trade.OrderEntitlementLookup.Status;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "learning-service", contextId = "orderEntitlementLookupClient")
public interface OrderEntitlementLookupClient {
    @PostMapping("/internal/v1/learning-entitlements/order-details")
    List<Status> orderEntitlements(@RequestBody List<Line> lines);
}
