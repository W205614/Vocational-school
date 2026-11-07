package com.tianji.api.config;

import com.tianji.api.client.privacy.PrivacyDomainClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PrivacyFeignRegistrationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(basePackages = "com.tianji.api.client.privacy")
    static class Clients {}

    @Test
    void actualFeignScannerRegistersNestedContractsAndDomainMethodsWithoutNetworkCalls() {
        var expected =
                Set.of(
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
                        "message",
                        "data");
        String[] properties =
                expected.stream()
                        .map(
                                domain ->
                                        "spring.cloud.openfeign.client.config.privacy-"
                                                + domain
                                                + ".url=http://127.0.0.1:9")
                        .toArray(String[]::new);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class))
                .withUserConfiguration(Clients.class)
                .withPropertyValues(properties)
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            var clients =
                                    context.getBeansOfType(PrivacyDomainClient.class).values();
                            assertEquals(13, clients.size());
                            assertEquals(
                                    expected,
                                    clients.stream()
                                            .map(PrivacyDomainClient::domain)
                                            .collect(java.util.stream.Collectors.toSet()));
                        });
    }
}
