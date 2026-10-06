package com.tianji.common.autoconfigure.reliability;
import io.micrometer.core.instrument.*;import org.springframework.beans.factory.SmartInitializingSingleton;import org.springframework.context.ApplicationContext;import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
public final class ManagedExecutorMetrics implements SmartInitializingSingleton{
 private final ApplicationContext context;private final MeterRegistry meters;
 public ManagedExecutorMetrics(ApplicationContext context,MeterRegistry meters){this.context=context;this.meters=meters;}
 @Override public void afterSingletonsInstantiated(){
  context.getBeansOfType(ThreadPoolTaskExecutor.class).forEach((name,pool)->{
   Gauge.builder("managed.executor.active",pool,ThreadPoolTaskExecutor::getActiveCount).tag("pool",name).register(meters);
   Gauge.builder("managed.executor.queued",pool,ThreadPoolTaskExecutor::getQueueSize).tag("pool",name).register(meters);
   Gauge.builder("managed.executor.queue.remaining",pool,p->p.getThreadPoolExecutor().getQueue().remainingCapacity()).tag("pool",name).register(meters);
  });
 }
}
