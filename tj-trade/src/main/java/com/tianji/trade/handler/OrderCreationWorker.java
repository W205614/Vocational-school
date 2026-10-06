package com.tianji.trade.handler;
import com.tianji.common.autoconfigure.reliability.OperationStore;
import com.tianji.common.utils.UserContext;
import com.tianji.trade.domain.dto.PlaceOrderDTO;
import com.tianji.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;
@Component
public class OrderCreationWorker {
    private final OperationStore operations;private final IOrderService orders;private final JsonMapper json;private final ThreadPoolTaskExecutor executor;
    public OrderCreationWorker(OperationStore operations,IOrderService orders,JsonMapper json,@Qualifier("orderCreationExecutor") ThreadPoolTaskExecutor executor) {
        this.operations=operations;this.orders=orders;this.json=json;this.executor=executor;
    }
    @Scheduled(fixedDelayString="${tj.trade.creation-interval-ms:500}") public void poll() {
        for(String kind:java.util.List.of("ORDER_CREATE","FREE_ENROLL")) for(var work:operations.due(kind,20)) {
            try { executor.execute(()->{
                String token=UUID.randomUUID().toString();
                if(!operations.claim(work.id(),token)) return;
                operations.executeWorkflow(work,token,()->{
                    try {UserContext.setUser(work.userId());return kind.equals("FREE_ENROLL")?orders.enrolledFreeCourse(json.readTree(work.payload()).get("courseId").asLong()):orders.placeOrder(json.readValue(work.payload(),PlaceOrderDTO.class));}
                    finally {UserContext.removeUser();}
                });
            }); } catch(org.springframework.core.task.TaskRejectedException rejected) {break;}
        }
    }
}
