package com.tianji.common.autoconfigure.reliability;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
public class OutboxDispatcher {
    private com.tianji.common.autoconfigure.reliability.AcceptanceFaults faults;
    public void setAcceptanceFaults(AcceptanceFaults faults){this.faults=faults;}
    private final OutboxStore store;private final RabbitMqHelper sender;private final MeterRegistry metrics;private final ThreadPoolTaskExecutor executor;
    public OutboxDispatcher(OutboxStore store,RabbitMqHelper sender,MeterRegistry metrics,ThreadPoolTaskExecutor executor) {
        this.store=store;this.sender=sender;this.metrics=metrics;this.executor=executor;
        metrics.gauge("reliability.outbox.workers",executor,ThreadPoolTaskExecutor::getActiveCount);
        metrics.gauge("reliability.outbox.queue",executor,ThreadPoolTaskExecutor::getQueueSize);
    }
    @Scheduled(fixedDelayString="${tj.reliability.dispatch-interval-ms:500}") public void dispatch() {
        for(var event:store.candidates(50)) {
            try {executor.execute(()->{
                String token=UUID.randomUUID().toString();if(!store.claim(event.id(),token)) return;
                try {
                    sender.sendStored(event.exchange(),event.routingKey(),event.payload(),event.id(),event.businessKey(),event.type(),event.version(),0);
                    if(faults!=null)faults.afterPublish(event.businessKey());
                    store.sent(event.id(),token);metrics.counter("reliability.outbox.sent").increment();
                } catch(Exception e) {store.failed(event,token,e);metrics.counter("reliability.outbox.failed").increment();}
            });} catch(org.springframework.core.task.TaskRejectedException rejected) {
                metrics.counter("reliability.outbox.rejected").increment();break;
            }
        }
    }
}
