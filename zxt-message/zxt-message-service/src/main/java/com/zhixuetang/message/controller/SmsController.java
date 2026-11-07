package com.zhixuetang.message.controller;

import com.zhixuetang.api.dto.sms.SmsInfoDTO;
import com.zhixuetang.message.service.ISmsService;
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
    private final com.zhixuetang.common.autoconfigure.reliability.OperationStore operations;

    @Operation(summary = "持久受理短信任务；202不代表供应商受理或送达")
    @PostMapping("message")
    public org.springframework.http.ResponseEntity<?> sendMessage(
            @RequestBody SmsInfoDTO smsInfoDTO,
            @org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key") String key) {
        com.zhixuetang.common.utils.UserContext.requireAdmin();
        return org.springframework.http.ResponseEntity.accepted()
                .body(
                        operations.submit(
                                com.zhixuetang.common.utils.UserContext.requireUser(),
                                "SMS_SEND",
                                key,
                                smsInfoDTO));
    }
}
