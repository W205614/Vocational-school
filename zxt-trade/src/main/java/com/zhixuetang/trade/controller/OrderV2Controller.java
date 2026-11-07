package com.zhixuetang.trade.controller;

import com.zhixuetang.common.autoconfigure.reliability.OperationStore;
import com.zhixuetang.common.utils.UserContext;
import com.zhixuetang.trade.domain.dto.PlaceOrderDTO;
import com.zhixuetang.trade.domain.query.OrderPageQuery;
import com.zhixuetang.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/orders")
public class OrderV2Controller {
    private final IOrderService orders;
    private final OperationStore operations;

    @PostMapping
    public ResponseEntity<OperationStore.View> create(
            @Valid @RequestBody PlaceOrderDTO request,
            @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.accepted()
                .body(operations.submit(UserContext.requireUser(), "ORDER_CREATE", key, request));
    }

    @GetMapping
    public Object list(OrderPageQuery query) {
        UserContext.requireUser();
        return orders.queryMyOrderPage(query);
    }

    @PostMapping("/free-courses/{courseId}")
    public ResponseEntity<OperationStore.View> enroll(
            @PathVariable Long courseId, @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.accepted()
                .body(
                        operations.submit(
                                UserContext.requireUser(),
                                "FREE_ENROLL",
                                key,
                                java.util.Map.of("courseId", courseId)));
    }

    @GetMapping("/confirmation")
    public Object confirm(
            @RequestParam List<Long> courseIds,
            @RequestParam(required = false) List<Long> couponIds) {
        UserContext.requireUser();
        return orders.prePlaceOrder(courseIds, couponIds);
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable Long id) {
        return orders.queryOrderById(id);
    }

    @GetMapping("/{id}/status")
    public Object status(@PathVariable Long id) {
        return orders.queryOrderStatus(id);
    }

    @PutMapping("/{id}/cancel")
    public void cancel(@PathVariable Long id) {
        orders.cancelOrder(id);
    }
}
