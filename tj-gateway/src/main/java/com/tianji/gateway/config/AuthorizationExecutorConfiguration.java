package com.tianji.gateway.config;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.scheduler.*;
@Configuration(proxyBeanMethods=false)
public class AuthorizationExecutorConfiguration {
 @Bean public ThreadPoolTaskExecutor authorizationExecutor(){
  var pool=new ThreadPoolTaskExecutor();pool.setCorePoolSize(8);pool.setMaxPoolSize(8);pool.setQueueCapacity(100);
  pool.setThreadNamePrefix("gateway-auth-");pool.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
  pool.setWaitForTasksToCompleteOnShutdown(true);pool.setAwaitTerminationSeconds(30);return pool;
 }
 @Bean(destroyMethod="dispose") public Scheduler authorizationScheduler(ThreadPoolTaskExecutor authorizationExecutor){return Schedulers.fromExecutor(authorizationExecutor);}
}
