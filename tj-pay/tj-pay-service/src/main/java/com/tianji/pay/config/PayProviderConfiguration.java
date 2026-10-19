package com.tianji.pay.config;
import com.tianji.pay.third.IPayService;
import com.tianji.pay.third.wx.config.WxPayProperties;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ListableBeanFactory;
import java.util.Map;
@Configuration @EnableConfigurationProperties(WxPayProperties.class)
public class PayProviderConfiguration {
 @Bean(name="providerReconciliationExecutor") public org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor reconciliationExecutor(){
  var executor=new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
  executor.setCorePoolSize(4);executor.setMaxPoolSize(4);executor.setQueueCapacity(100);
  executor.setThreadNamePrefix("provider-reconcile-");executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
  executor.setWaitForTasksToCompleteOnShutdown(true);executor.setAwaitTerminationSeconds(30);return executor;
 }
 @Bean(name="payServiceChannels") public Map<String,IPayService> channels(ListableBeanFactory factory){return Map.copyOf(factory.getBeansOfType(IPayService.class));}
}
