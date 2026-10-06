package com.tianji.authsdk.gateway.config;

import com.tianji.authsdk.gateway.util.AuthUtil;
import com.tianji.authsdk.gateway.util.JwtSignerHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@org.springframework.scheduling.annotation.EnableScheduling
public class AuthAutoConfiguration {
    @Bean public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler jwtTaskScheduler(){
        var scheduler=new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("jwt-key-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);scheduler.setAwaitTerminationSeconds(5);return scheduler;
    }

    @Bean
    @ConditionalOnClass(DiscoveryClient.class)
    public JwtSignerHolder jwtSignerHolder(DiscoveryClient discoveryClient){
        return new JwtSignerHolder(discoveryClient);
    }

    @Bean
    public AuthUtil authUtil(JwtSignerHolder jwtSignerHolder, StringRedisTemplate stringRedisTemplate){
        return new AuthUtil(jwtSignerHolder, stringRedisTemplate);
    }
}
