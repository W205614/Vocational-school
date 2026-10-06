package com.tianji.trade.controller;
import com.tianji.common.autoconfigure.reliability.OperationStore;
import com.tianji.common.utils.UserContext;
import com.tianji.trade.domain.dto.PlaceOrderDTO;
import com.tianji.trade.domain.query.OrderPageQuery;
import com.tianji.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v2/orders")
public class OrderV2Controller {
    private final IOrderService orders;private final OperationStore operations;
    @PostMapping public ResponseEntity<OperationStore.View> create(@Valid @RequestBody PlaceOrderDTO request,@RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.accepted().body(operations.submit(UserContext.requireUser(),"ORDER_CREATE",key,request));
    }
    @GetMapping public Object list(OrderPageQuery query) {UserContext.requireUser();return orders.queryMyOrderPage(query);}
    @PostMapping("/free-courses/{courseId}") public ResponseEntity<OperationStore.View> enroll(@PathVariable Long courseId,@RequestHeader("Idempotency-Key") String key){
        return ResponseEntity.accepted().body(operations.submit(UserContext.requireUser(),"FREE_ENROLL",key,java.util.Map.of("courseId",courseId)));
    }
    @GetMapping("/confirmation") public Object confirm(@RequestParam List<Long> courseIds) {UserContext.requireUser();return orders.prePlaceOrder(courseIds);}
    @GetMapping("/{id}") public Object detail(@PathVariable Long id) {return orders.queryOrderById(id);}
    @GetMapping("/{id}/status") public Object status(@PathVariable Long id) {return orders.queryOrderStatus(id);}
    @PutMapping("/{id}/cancel") public void cancel(@PathVariable Long id) {orders.cancelOrder(id);}
}
