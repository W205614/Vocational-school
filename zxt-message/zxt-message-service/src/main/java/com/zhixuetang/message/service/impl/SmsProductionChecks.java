package com.zhixuetang.message.service.impl;

import com.zhixuetang.api.dto.sms.SmsInfoDTO;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.Arrays;

@Component
public class SmsProductionChecks implements ApplicationRunner {
    private final Environment environment;
    private final SmsProviderRegistry providers;

    public SmsProductionChecks(Environment environment, SmsProviderRegistry providers) {
        this.environment = environment;
        this.providers = providers;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("production")
                && !environment.getProperty("zxt.production.enabled", Boolean.class, false)) return;
        for (String name : new String[] {"zxt.sms.ali.accessId", "zxt.sms.ali.accessSecret"}) {
            if (environment.getProperty(name, "").isBlank())
                throw new IllegalStateException("Missing production SMS credential: " + name);
        }
        var verification = new SmsInfoDTO();
        verification.setTemplateCode("VERIFY_CODE");
        providers.select(verification);
    }
}
