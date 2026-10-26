package com.tianji.user.controller;

import com.tianji.common.utils.*;
import com.tianji.user.service.impl.PublicAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v2/public/accounts")
public class PublicAccountController {
    private final PublicAccountService accounts;
    public record CodeRequest(String phone,String purpose){}
    @GetMapping("/privacy") public Map<String,Object> policy(){InternalAuth.requireService();return accounts.policy();}
    @PostMapping("/verification-codes") public ResponseEntity<?> code(@RequestBody CodeRequest form){
        InternalAuth.requireService();accounts.requestCode(form.phone(),form.purpose(),Objects.toString(WebUtils.getHeader("X-Client-IP"),"unknown"));
        return ResponseEntity.accepted().body(Map.of("status","ACCEPTED","message","验证码申请已受理，送达结果取决于通知渠道"));
    }
    @PostMapping("/registration") public void register(@RequestBody PublicAccountService.Registration form){InternalAuth.requireService();accounts.register(form);}
    @PostMapping("/password-recovery") public void recover(@RequestBody PublicAccountService.Recovery form){InternalAuth.requireService();accounts.recover(form);}
}
