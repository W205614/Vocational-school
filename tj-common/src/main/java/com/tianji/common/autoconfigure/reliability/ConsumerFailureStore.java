package com.tianji.common.autoconfigure.reliability;
import com.tianji.common.exceptions.ConflictException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;
public class ConsumerFailureStore {
 private final JdbcTemplate jdbc;private final RabbitTemplate rabbit;
 public ConsumerFailureStore(JdbcTemplate jdbc,RabbitTemplate rabbit){this.jdbc=jdbc;this.rabbit=rabbit;}
 public void save(Message message,Throwable error){
  var p=message.getMessageProperties();String id=p.getMessageId(),queue=p.getConsumerQueue();
  if(id==null || id.isBlank() || queue==null || queue.isBlank())throw new IllegalArgumentException("Failure requires message ID and consumer queue");
  Throwable cause=error;var visited=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>());
  while(cause.getCause()!=null && visited.add(cause))cause=cause.getCause();
  String reason=cause.getClass().getSimpleName()+": "+Objects.toString(cause.getMessage(),"");
  jdbc.update("INSERT INTO reliability_consumer_failure(failure_id,event_id,queue_name,body,content_type,last_error) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE status='FAILED',attempts=attempts+1,last_error=VALUES(last_error),version=version+1",UUID.randomUUID().toString(),id,queue,message.getBody(),p.getContentType(),reason.substring(0,Math.min(1000,reason.length())));
 }
 public List<Map<String,Object>> failures(int limit){
  return jdbc.queryForList("SELECT failure_id,event_id,queue_name,status,attempts,last_error,version,created_at FROM reliability_consumer_failure WHERE status='FAILED' ORDER BY created_at DESC LIMIT ?",Math.min(100,Math.max(1,limit)));
 }
 public void replay(String id,long version){
  var rows=jdbc.queryForList("SELECT * FROM reliability_consumer_failure WHERE failure_id=? AND status='FAILED' AND version=?",id,version);
  if(rows.isEmpty())throw new ConflictException("失败任务状态或版本已改变");
  var row=rows.getFirst();var p=new MessageProperties();p.setMessageId(row.get("event_id").toString());
  p.setContentType(Objects.toString(row.get("content_type"),"application/json"));p.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
  var correlation=new CorrelationData(UUID.randomUUID().toString());
  rabbit.send("",row.get("queue_name").toString(),new Message((byte[])row.get("body"),p),correlation);
  try{var confirm=correlation.getFuture().get(5,TimeUnit.SECONDS);
   if(!confirm.ack() || correlation.getReturned()!=null)throw new IllegalStateException("Replay not confirmed or unroutable");
  }catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("Replay interrupted; original task retained",error);}
   catch(Exception error){throw new IllegalStateException("Replay uncertain; original task retained",error);}
  // A crash before this update permits a duplicate; the consumer inbox makes it harmless.
  if(jdbc.update("UPDATE reliability_consumer_failure SET status='REPLAYED',version=version+1 WHERE failure_id=? AND status='FAILED' AND version=?",id,version)!=1)
   throw new ConflictException("重放已发送，但任务版本已改变，请重新查询");
 }
}
