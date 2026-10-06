package com.tianji.promotion.controller;
import com.tianji.common.autoconfigure.reliability.OperationStore;
import com.tianji.common.exceptions.UnauthorizedException;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.service.impl.CouponClaimService.Request;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v2")
@RequiredArgsConstructor
public class CouponV2Controller {
    private final OperationStore operations;
    @PostMapping("/coupons/{id}/claims")
    public ResponseEntity<OperationStore.View> claim(@PathVariable Long id,@RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.accepted().body(operations.submit(user(),"COUPON_CLAIM",key,new Request(id,null)));
    }
    @PostMapping("/coupon-exchanges")
    public ResponseEntity<OperationStore.View> exchange(@RequestBody Request request,@RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.accepted().body(operations.submit(user(),"COUPON_CLAIM",key,new Request(null,request.code())));
    }
    private static long user() { Long id=UserContext.getUser();if(id==null) throw new UnauthorizedException("请先登录");return id; }
}
