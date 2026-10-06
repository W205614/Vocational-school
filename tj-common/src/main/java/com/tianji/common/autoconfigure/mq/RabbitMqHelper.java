package com.tianji.common.autoconfigure.mq;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.DisposableBean;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.*;
import static com.tianji.common.constants.Constant.REQUEST_ID_HEADER;

/** Confirms are broker acceptance, never a substitute for a transactional outbox. */
@Slf4j
public class RabbitMqHelper implements DisposableBean {
    private final RabbitTemplate template;
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    public RabbitMqHelper(RabbitTemplate template) {
        this.template = template;
        executor.setCorePoolSize(2); executor.setMaxPoolSize(4); executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("mq-send-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true); executor.setAwaitTerminationSeconds(30);
        executor.initialize();
    }
    public <T> void send(String exchange, String routingKey, T body) {
        publish(exchange, routingKey, body, Duration.ZERO);
    }
    public <T> void sendDelayMessage(String exchange, String routingKey, T body, Duration delay) {
        publish(exchange, routingKey, body, delay);
    }
    private <T> void publish(String exchange, String routingKey, T body, Duration delay) {
        String id = UUID.randomUUID().toString();
        CorrelationData data = new CorrelationData(id);
        template.convertAndSend(exchange, routingKey, body, message -> {
            new BasicIdMessageProcessor().postProcessMessage(message);
            message.getMessageProperties().setMessageId(id);
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            if (!delay.isZero()) message.getMessageProperties().setHeader("x-delay", delay.toMillis());
            return message;
        }, data);
        awaitConfirm(data);
    }
    public void sendStored(String exchange, String routingKey, String payload, String eventId,
                           String businessKey, String eventType, int schemaVersion, long delayMs) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setMessageId(eventId); props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setHeader("businessKey", businessKey); props.setHeader("eventType", eventType);
        props.setHeader("schemaVersion", schemaVersion);
        props.setHeader(REQUEST_ID_HEADER, eventId);
        if (delayMs > 0) props.setHeader("x-delay", delayMs);
        CorrelationData data = new CorrelationData(eventId);
        template.send(exchange, routingKey, new Message(payload.getBytes(StandardCharsets.UTF_8), props), data);
        awaitConfirm(data);
    }
    private void awaitConfirm(CorrelationData data) {
        try {
            var confirm = data.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck() || data.getReturned() != null)
                throw new IllegalStateException("Message not accepted/routed: " + data.getId());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Publish interrupted", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Publish outcome uncertain; retry with same event ID", ex);
        }
    }
    public <T> CompletableFuture<Void> sendAsync(String exchange, String routingKey, T body, Long delayMs) {
        Map<String,String> context = MDC.getCopyOfContextMap();
        return CompletableFuture.runAsync(() -> {
            try {
                if (context != null) MDC.setContextMap(context);
                publish(exchange, routingKey, body, Duration.ofMillis(delayMs == null ? 0 : delayMs));
            } finally { MDC.clear(); }
        }, executor);
    }
    public <T> CompletableFuture<Void> sendAsync(String exchange, String routingKey, T body) {
        return sendAsync(exchange, routingKey, body, null);
    }
    @Override public void destroy() { executor.shutdown(); }
}
