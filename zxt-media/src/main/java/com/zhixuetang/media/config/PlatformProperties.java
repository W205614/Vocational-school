package com.zhixuetang.media.config;

import com.zhixuetang.media.enums.Platform;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "zxt.platform")
public class PlatformProperties {
    private Platform file;
    private Platform media;
}
