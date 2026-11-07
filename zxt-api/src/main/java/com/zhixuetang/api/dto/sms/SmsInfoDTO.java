package com.zhixuetang.api.dto.sms;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "短信发送参数")
public class SmsInfoDTO {
    private String templateCode;
    private Iterable<String> phones;
    private Map<String, String> templateParams;

    @Schema(description = "最晚发送时间，Unix毫秒；验证码必须提供")
    private Long expiresAt;

    @Override
    public String toString() {
        return "SmsInfoDTO[templateCode="
                + templateCode
                + ", recipients=REDACTED, parameters=REDACTED]";
    }
}
