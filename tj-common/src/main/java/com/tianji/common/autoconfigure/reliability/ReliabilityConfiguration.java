package com.tianji.common.autoconfigure.reliability;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;

@AutoConfiguration(afterName={"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration","org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration","org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration","org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration","com.tianji.common.autoconfigure.mq.MqConfig"})
@ConditionalOnClass(name="org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(name="tj.reliability.enabled",havingValue="true",matchIfMissing=true)
@EnableScheduling
public class ReliabilityConfiguration {
    @Bean public ManagedExecutorMetrics managedExecutorMetrics(org.springframework.context.ApplicationContext context,MeterRegistry meters){return new ManagedExecutorMetrics(context,meters);}
    @Bean public OutboxStore outboxStore(JdbcTemplate jdbc,JsonMapper json) { return new OutboxStore(jdbc,json); }
    @Bean public InboxStore inboxStore(JdbcTemplate jdbc,PlatformTransactionManager manager) {
        return new InboxStore(jdbc,new TransactionTemplate(manager));
    }
    @Bean public OperationStore operationStore(JdbcTemplate jdbc,JsonMapper json,PlatformTransactionManager manager) {
        return new OperationStore(jdbc,json,new TransactionTemplate(manager));
    }
    @Bean public ReliabilityMetrics reliabilityMetrics(JdbcTemplate jdbc,MeterRegistry meters){return new ReliabilityMetrics(jdbc,meters);}
    @Bean public OperationWorker operationWorker(OperationStore store,org.springframework.beans.factory.ObjectProvider<OperationHandler> handlers,
            @org.springframework.beans.factory.annotation.Qualifier("operationExecutor") org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor) {
        return new OperationWorker(store,handlers,executor);
    }
    @Bean public org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor operationExecutor() {
        var pool=new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        pool.setCorePoolSize(4);pool.setMaxPoolSize(8);pool.setQueueCapacity(100);pool.setThreadNamePrefix("operation-");
        pool.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        pool.setWaitForTasksToCompleteOnShutdown(true);pool.setAwaitTerminationSeconds(30);return pool;
    }
    @Bean public OperationFailureController operationFailureController(OperationStore store){return new OperationFailureController(store);}
    @Bean public OperationController operationController(OperationStore store) { return new OperationController(store); }
    @Bean public EventAdminController eventAdminController(OutboxStore store) {return new EventAdminController(store);}
    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    @ConditionalOnClass(name="org.springframework.amqp.rabbit.core.RabbitTemplate")
    static class Messaging {
    @Bean
    public ConsumerFailureStore consumerFailureStore(JdbcTemplate jdbc,org.springframework.amqp.rabbit.core.RabbitTemplate rabbit) {return new ConsumerFailureStore(jdbc,rabbit);}
    @Bean
    public ConsumerFailureController consumerFailureController(ConsumerFailureStore store) {return new ConsumerFailureController(store);}
    @Bean
    public OutboxDispatcher outboxDispatcher(OutboxStore store,RabbitMqHelper helper,MeterRegistry metrics,
            @org.springframework.beans.factory.annotation.Qualifier("outboxExecutor") org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor,AcceptanceFaults faults) {
        var dispatcher=new OutboxDispatcher(store,helper,metrics,executor);dispatcher.setAcceptanceFaults(faults);return dispatcher;
    }
    }
    @Bean public org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor outboxExecutor() {
        var pool=new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);pool.setMaxPoolSize(4);pool.setQueueCapacity(100);pool.setThreadNamePrefix("outbox-");
        pool.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        pool.setWaitForTasksToCompleteOnShutdown(true);pool.setAwaitTerminationSeconds(30);return pool;
    }
}
