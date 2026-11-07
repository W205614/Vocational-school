package com.zhixuetang.api.config;

import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.zhixuetang.common.constants.Constant.*;

@Configuration
@EnableFeignClients(basePackages = "com.zhixuetang.api.client")
public class RequestIdRelayConfiguration {
    @Bean(destroyMethod = "close")
    public FeignBulkheadCapability upstreamBulkhead(
            org.springframework.core.env.Environment environment) {
        String ports = environment.getProperty("zxt.feign.internal-ports", "");
        var internalPorts =
                java.util.Arrays.stream(ports.split(","))
                        .filter(value -> !value.isBlank())
                        .map(String::trim)
                        .map(Integer::valueOf)
                        .collect(java.util.stream.Collectors.toSet());
        return new FeignBulkheadCapability(
                environment.getProperty("zxt.feign.max-concurrent", Integer.class, 32),
                internalPorts);
    }

    @Bean
    public RequestInterceptor requestIdInterceptor() {
        return template -> {
            String target =
                    template.feignTarget() == null ? template.url() : template.feignTarget().url();
            var credential = com.zhixuetang.common.utils.ServiceIdentity.current().outgoing(target);
            template.removeHeader("X-Internal-Token");
            template.removeHeader("X-ZXT-Service");
            template.header("X-Internal-Token", credential.token());
            if (credential.caller() != null) template.header("X-ZXT-Service", credential.caller());
            Long user = com.zhixuetang.common.utils.UserContext.getUser();
            if (user != null) template.header("user-info", user.toString());
            Long role = com.zhixuetang.common.utils.UserContext.getRole();
            if (role != null) template.header("user-role", role.toString());
            int depth = com.zhixuetang.common.utils.UserContext.getCallDepth();
            if (depth < 0 || depth >= 4)
                throw new IllegalStateException(
                        "Internal HTTP call depth exceeds reserved capacity");
            template.header("X-ZXT-Call-Depth", Integer.toString(depth + 1));
            template.header(REQUEST_ID_HEADER, MDC.get(REQUEST_ID_HEADER))
                    .header(REQUEST_FROM_HEADER, FEIGN_ORIGIN_NAME);
        };
    }
}
