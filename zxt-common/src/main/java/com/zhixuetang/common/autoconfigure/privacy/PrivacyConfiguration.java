package com.zhixuetang.common.autoconfigure.privacy;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration(
        afterName = "com.zhixuetang.common.autoconfigure.reliability.ReliabilityConfiguration")
@ConditionalOnBean(JdbcTemplate.class)
@org.springframework.context.annotation.Conditional(PrivacyConfiguration.BusinessModule.class)
@ConditionalOnProperty(name = "zxt.privacy.enabled", havingValue = "true", matchIfMissing = true)
public class PrivacyConfiguration {
    static class BusinessModule implements org.springframework.context.annotation.Condition {
        public boolean matches(
                org.springframework.context.annotation.ConditionContext context,
                org.springframework.core.type.AnnotatedTypeMetadata metadata) {
            return java.util.Set.of(
                            "auth",
                            "user",
                            "trade",
                            "pay",
                            "promotion",
                            "course",
                            "learning",
                            "exam",
                            "remark",
                            "media",
                            "search",
                            "message")
                    .contains(
                            context.getEnvironment()
                                    .getProperty("spring.application.name", "")
                                    .replaceFirst("-service$", ""));
        }
    }

    @Bean
    public PrivacyDomain privacyDomain(
            JdbcTemplate jdbc, PlatformTransactionManager manager, JsonMapper json, Environment env)
            throws java.io.IOException {
        String alias = env.getProperty("spring.application.name", "").replaceFirst("-service$", "");
        var resource =
                new org.springframework.core.io.ClassPathResource(
                        "META-INF/zhixuetang/privacy/" + alias + ".json");
        if (!resource.exists())
            throw new IllegalStateException("Missing reviewed privacy inventory for " + alias);
        try (var input = resource.getInputStream()) {
            return new PrivacyDomain(
                    json.readValue(input, PrivacyDomain.Definition.class),
                    jdbc,
                    new TransactionTemplate(manager),
                    json);
        }
    }

    @Bean
    public PrivacyDomainController privacyDomainController(PrivacyDomain domain) {
        return new PrivacyDomainController(domain);
    }
}
