package com.tianji.trade.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {

    @Bean
    public ThreadPoolTaskExecutor sendRefundRequestExecutor(){
        ThreadPoolTaskExecutor refundExecutor = new ThreadPoolTaskExecutor();
        //配置核心线程数
        refundExecutor.setCorePoolSize(4);
        //配置最大线程数
        refundExecutor.setMaxPoolSize(8);
        //配置队列大小
        refundExecutor.setQueueCapacity(100);
        //配置线程池中的线程的名称前缀
        refundExecutor.setThreadNamePrefix("pd-user-async-service-");
        // 由调用者线程执行
        refundExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        refundExecutor.setWaitForTasksToCompleteOnShutdown(true);
        refundExecutor.setAwaitTerminationSeconds(30);
        return refundExecutor;
    }
}
