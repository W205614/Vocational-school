package com.zhixuetang.pay.third.ali.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "zxt.pay.ali")
public class AliPayProperties {
    private String protocol = "https";
    private String gatewayHost = "openapi.alipay.com";
    private String signType = "RSA2";
    private String appId;
    private String sellerId;
    private String merchantPrivateKey;
    private String publicKey;
    private String encryptKey;
}
