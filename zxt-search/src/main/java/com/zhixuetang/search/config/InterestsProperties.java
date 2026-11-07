package com.zhixuetang.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "zxt.interests")
public class InterestsProperties {
    private int topNumber;
}
