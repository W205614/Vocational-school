package com.tianji.api.config;


import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.tianji.common.constants.Constant.*;

@Configuration
@EnableFeignClients(basePackages = "com.tianji.api.client")
public class RequestIdRelayConfiguration {
    @Bean
    public feign.Capability upstreamBulkhead(org.springframework.core.env.Environment environment){
        return new FeignBulkheadCapability(environment.getProperty("tj.feign.max-concurrent",Integer.class,16));
    }

    @Bean
    public RequestInterceptor requestIdInterceptor(){
        return template -> {
            String internal=System.getenv("TJ_INTERNAL_TOKEN");
            if(internal!=null) template.header("X-Internal-Token",internal);
            Long user=com.tianji.common.utils.UserContext.getUser();
            if(user!=null) template.header("user-info",user.toString());
            Long role=com.tianji.common.utils.UserContext.getRole();
            if(role!=null) template.header("user-role",role.toString());
            template
                .header(REQUEST_ID_HEADER, MDC.get(REQUEST_ID_HEADER))
                .header(REQUEST_FROM_HEADER, FEIGN_ORIGIN_NAME);
        };
    }
}
