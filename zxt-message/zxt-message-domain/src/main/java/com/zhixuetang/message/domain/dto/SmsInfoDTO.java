package com.zhixuetang.message.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "短信发送参数")
public class SmsInfoDTO {
    @Schema(description = "模板代号")
    private String templateCode;

    @Schema(description = "手机号码")
    private Iterable<String> phones;

    @Schema(description = "模板参数")
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
