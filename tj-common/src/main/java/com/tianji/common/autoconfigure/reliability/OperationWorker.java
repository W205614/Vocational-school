package com.tianji.common.autoconfigure.reliability;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.ObjectProvider;
import java.util.*;
public class OperationWorker {
 @org.springframework.beans.factory.annotation.Value("${tj.reliability.operation-batch-size:100}") private int batch=100;
 private final OperationStore store;private final ObjectProvider<OperationHandler> handlers;private final ThreadPoolTaskExecutor executor;
 public OperationWorker(OperationStore store,ObjectProvider<OperationHandler> handlers,ThreadPoolTaskExecutor executor){this.store=store;this.handlers=handlers;this.executor=executor;}
 @Scheduled(fixedDelayString="${tj.reliability.operation-interval-ms:500}")
 public void poll(){
  Map<String,OperationHandler> byKind=new LinkedHashMap<>();
  handlers.orderedStream().forEach(h->{if(byKind.put(h.kind(),h)!=null)throw new IllegalStateException("Duplicate operation handler: "+h.kind());});
  // Query by kind, so a remote workflow backlog cannot starve unrelated local operations.
  for(var entry:byKind.entrySet()) {
   int capacity=Math.min(Math.max(1,Math.min(batch,100)),executor.getThreadPoolExecutor().getQueue().remainingCapacity()+Math.max(0,executor.getMaxPoolSize()-executor.getActiveCount()));
   if(capacity<1)return;
   for(var work:store.due(entry.getKey(),capacity)) {
    String token=UUID.randomUUID().toString();if(!store.claim(work.id(),token))continue;
    try{executor.execute(()->store.execute(work,token,entry.getValue()));}
    catch(java.util.concurrent.RejectedExecutionException full){store.releaseUnstarted(work.id(),token);return;}
   }
  }
 }
}
