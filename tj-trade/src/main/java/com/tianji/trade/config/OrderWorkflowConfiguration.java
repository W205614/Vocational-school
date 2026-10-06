package com.tianji.trade.config;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;
@Configuration(proxyBeanMethods=false)
public class OrderWorkflowConfiguration {
    @Bean public ThreadPoolTaskExecutor orderCreationExecutor() {
        var executor=new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);executor.setMaxPoolSize(4);executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("order-creation-");executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);executor.setAwaitTerminationSeconds(30);return executor;
    }
}
