package com.tianji.common.autoconfigure.mq;

import tools.jackson.databind.json.JsonMapper;
import com.tianji.common.utils.StringUtils;
import org.slf4j.MDC;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import static com.tianji.common.constants.Constant.REQUEST_ID_HEADER;
import static com.tianji.common.constants.MqConstants.Exchange.ERROR_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.ERROR_KEY_PREFIX;
import static com.tianji.common.constants.MqConstants.Queue.ERROR_QUEUE_TEMPLATE;


@Configuration
@ConditionalOnClass(value = {MessageConverter.class, AmqpTemplate.class})
public class MqConfig implements EnvironmentAware{

    private String defaultErrorRoutingKey;
    private String defaultErrorQueue;

    @Bean(name = "rabbitListenerContainerFactory")
    @ConditionalOnProperty(prefix = "spring.rabbitmq.listener", name = "type", havingValue = "simple",
            matchIfMissing = true)
    SimpleRabbitListenerContainerFactory simpleRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
            ObjectProvider<ContainerCustomizer<SimpleMessageListenerContainer>> simpleContainerCustomizer,
            com.tianji.common.autoconfigure.reliability.AcceptanceFaults faults) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        simpleContainerCustomizer.ifUnique(factory::setContainerCustomizer);
        org.aopalliance.intercept.MethodInterceptor mdcAdvice=invocation -> {
            MDC.remove(REQUEST_ID_HEADER);
            try {
                for(Object argument:invocation.getArguments()) if(argument instanceof Message message) {
                    Object header=message.getMessageProperties().getHeader(REQUEST_ID_HEADER);
                    if(header!=null) MDC.put(REQUEST_ID_HEADER,header.toString());
                }
                Object result=invocation.proceed();
                for(Object argument:invocation.getArguments()) if(argument instanceof Message message) {
                    Object key=message.getMessageProperties().getHeader("businessKey");
                    if(key!=null) faults.afterConsume(key.toString());
                }
                return result;
            } finally { MDC.remove(REQUEST_ID_HEADER); }
        };
        org.aopalliance.aop.Advice[] configured=factory.getAdviceChain();
        var chain=new java.util.ArrayList<org.aopalliance.aop.Advice>();chain.add(mdcAdvice);
        if(configured!=null) java.util.Collections.addAll(chain,configured);
        factory.setAdviceChain(chain.toArray(org.aopalliance.aop.Advice[]::new));
        return factory;
    }

    @Bean public com.tianji.common.autoconfigure.reliability.AcceptanceFaults acceptanceFaults(Environment environment) {
        return new com.tianji.common.autoconfigure.reliability.AcceptanceFaults(environment);
    }

    @Bean
    public MessageConverter messageConverter(JsonMapper mapper){
        // 1.定义消息转换器
        JacksonJsonMessageConverter jackson2JsonMessageConverter = new EnvelopeJsonMessageConverter(mapper);
        // 2.配置自动创建消息id，用于识别不同消息
        jackson2JsonMessageConverter.setCreateMessageIds(true);
        return jackson2JsonMessageConverter;
    }

    /**
     * <h1>消息处理失败的重试策略</h1>
     * 本地重试失败后，消息投递到专门的失败交换机和失败消息队列：error.queue
     */
    @Bean
    @ConditionalOnClass(MessageRecoverer.class)
    @ConditionalOnMissingBean
    public MessageRecoverer republishMessageRecoverer(RabbitTemplate rabbitTemplate,
            ObjectProvider<com.tianji.common.autoconfigure.reliability.ConsumerFailureStore> stores){
        return (message,error) -> {
            try {
                var store=stores.getIfAvailable();
                if(store==null) throw new IllegalStateException("Durable consumer failure store is unavailable");
                store.save(message,error);
            } catch(Exception persistenceFailure) {
                throw new org.springframework.amqp.ImmediateRequeueAmqpException("Failure persistence failed; do not acknowledge original message",persistenceFailure);
            } finally {MDC.remove(REQUEST_ID_HEADER);}
        };
    }

    /**
     * rabbitmq发送工具
     *
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(RabbitTemplate.class)
    public RabbitMqHelper rabbitMqHelper(RabbitTemplate rabbitTemplate){
        if (rabbitTemplate.getConnectionFactory() instanceof org.springframework.amqp.rabbit.connection.CachingConnectionFactory factory) {
            factory.setPublisherConfirmType(org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType.CORRELATED);
            factory.setPublisherReturns(true);
        }
        rabbitTemplate.setMandatory(true);
        return new RabbitMqHelper(rabbitTemplate);
    }

    /**
     * 专门接收处理失败的消息
     */
    @Bean
    public DirectExchange errorMessageExchange(){
        return new DirectExchange(ERROR_EXCHANGE);
    }

    @Bean
    public Queue errorQueue(){
        return new Queue(defaultErrorQueue, true);
    }

    @Bean
    public Binding errorBinding(Queue errorQueue, DirectExchange errorMessageExchange){
        return BindingBuilder.bind(errorQueue).to(errorMessageExchange).with(defaultErrorRoutingKey);
    }

    @Override
    public void setEnvironment(Environment environment) {
        String appName = environment.getProperty("spring.application.name");
        this.defaultErrorRoutingKey = ERROR_KEY_PREFIX + appName;
        this.defaultErrorQueue = StringUtils.format(ERROR_QUEUE_TEMPLATE, appName);
    }
}
