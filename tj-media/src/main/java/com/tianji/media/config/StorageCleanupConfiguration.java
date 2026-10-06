package com.tianji.media.config;
import org.springframework.context.annotation.*;import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
@Configuration(proxyBeanMethods=false)
public class StorageCleanupConfiguration{
 @Bean public ThreadPoolTaskExecutor storageCleanupExecutor(){var executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(2);executor.setMaxPoolSize(4);executor.setQueueCapacity(64);executor.setThreadNamePrefix("storage-cleanup-");executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());executor.setWaitForTasksToCompleteOnShutdown(true);executor.setAwaitTerminationSeconds(30);return executor;}
}
