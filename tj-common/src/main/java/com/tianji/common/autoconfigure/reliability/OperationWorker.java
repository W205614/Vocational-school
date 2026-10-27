package com.tianji.common.autoconfigure.reliability;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.ObjectProvider;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
public class OperationWorker {
 @org.springframework.beans.factory.annotation.Value("${tj.reliability.operation-batch-size:100}") private int batch=100;
 private final OperationStore store;private final ObjectProvider<OperationHandler> handlers;private final ThreadPoolTaskExecutor executor;
 private final AtomicInteger nextKind=new AtomicInteger();
 public OperationWorker(OperationStore store,ObjectProvider<OperationHandler> handlers,ThreadPoolTaskExecutor executor){this.store=store;this.handlers=handlers;this.executor=executor;}
 @Scheduled(fixedDelayString="${tj.reliability.operation-interval-ms:500}")
 public void poll(){
  Map<String,OperationHandler> byKind=new LinkedHashMap<>();
  handlers.orderedStream().forEach(h->{if(byKind.put(h.kind(),h)!=null)throw new IllegalStateException("Duplicate operation handler: "+h.kind());});
  if(byKind.isEmpty())return;
  // Limit each kind's share and rotate the first kind even when only one slot is
  // free. A continuously busy kind must not consume every newly available slot.
  var kinds=new ArrayList<>(byKind.entrySet());
  int start=Math.floorMod(nextKind.get(),kinds.size());
  int remaining=Math.max(1,Math.min(batch,100)),quota=Math.max(1,remaining/kinds.size());
  for(int offset=0;offset<kinds.size() && remaining>0;offset++) {
   int index=(start+offset)%kinds.size();var entry=kinds.get(index);
   int capacity=Math.min(Math.min(quota,remaining),executor.getThreadPoolExecutor().getQueue().remainingCapacity()+Math.max(0,executor.getMaxPoolSize()-executor.getActiveCount()));
   if(capacity<1)return;
   for(var work:store.due(entry.getKey(),capacity)) {
    String token=UUID.randomUUID().toString();if(!store.claim(work.id(),token))continue;
    // Empty/full polls must not spend another kind's turn. Advance only when
    // there is leased work to submit, including a rejected submission below.
    nextKind.set((index+1)%kinds.size());
    try{executor.execute(()->store.execute(work,token,entry.getValue()));remaining--;}
    catch(java.util.concurrent.RejectedExecutionException full){store.releaseUnstarted(work.id(),token);return;}
   }
  }
 }
}
