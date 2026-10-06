package com.tianji.promotion.controller;
import com.tianji.api.dto.promotion.CouponReservationDTO;
import com.tianji.common.utils.InternalAuth;
import com.tianji.promotion.service.impl.CouponReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor @RequestMapping("/internal/v2/coupon-reservations")
public class CouponReservationController {
    private final CouponReservationService service;
    @PutMapping("/{order}") public Map<String,Object> reserve(@PathVariable long order,@RequestBody CouponReservationDTO request) {
        InternalAuth.requireService();return service.reserve(order,request);
    }
    @PutMapping("/{order}/confirm") public void confirm(@PathVariable long order,@RequestParam long userId) {InternalAuth.requireService();service.transition(order,userId,true);}
    @PutMapping("/{order}/release") public void release(@PathVariable long order,@RequestParam long userId) {InternalAuth.requireService();service.transition(order,userId,false);}
}
