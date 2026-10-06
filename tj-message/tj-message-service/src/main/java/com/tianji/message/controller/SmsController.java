package com.tianji.message.controller;

import com.tianji.api.dto.sms.SmsInfoDTO;
import com.tianji.message.service.ISmsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "短信发送控制器")
@RestController
@RequestMapping("sms")
@RequiredArgsConstructor
public class SmsController {

    private final ISmsService smsService;
    private final com.tianji.common.autoconfigure.reliability.OperationStore operations;

    @Operation(summary = "同步发送短信")
    @PostMapping("message")
    public org.springframework.http.ResponseEntity<?> sendMessage(@RequestBody SmsInfoDTO smsInfoDTO,@org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key") String key){
        com.tianji.common.utils.UserContext.requireAdmin();
        return org.springframework.http.ResponseEntity.accepted().body(operations.submit(com.tianji.common.utils.UserContext.requireUser(),"SMS_SEND",key,smsInfoDTO));
    }
}
