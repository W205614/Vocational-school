package com.tianji.message.handler;
import com.tianji.message.service.INoticeTaskService;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;import org.springframework.beans.factory.annotation.Qualifier;import org.springframework.stereotype.Component;import java.util.*;
@Component
public class DurableNoticeWorker {
 private final JdbcTemplate jdbc;private final INoticeTaskService service;private final ThreadPoolTaskExecutor executor;
 public DurableNoticeWorker(JdbcTemplate jdbc,INoticeTaskService service,@Qualifier("asyncNoticeExecutor") ThreadPoolTaskExecutor executor){this.jdbc=jdbc;this.service=service;this.executor=executor;}
 @Scheduled(fixedDelay=1000) public void poll(){
  for(var row:jdbc.queryForList("SELECT id,delivery_attempts FROM notice_task WHERE finished=0 AND delivery_status='PENDING' AND push_time<=NOW(3) AND delivery_next_attempt<=NOW(3) ORDER BY push_time LIMIT 20")){
   try{executor.execute(()->process(row));}catch(org.springframework.core.task.TaskRejectedException rejected){break;}
  }
 }
 private void process(Map<String,Object> row){
  Object id=row.get("id");String token=UUID.randomUUID().toString();
  if(jdbc.update("UPDATE notice_task SET delivery_token=?,delivery_attempts=delivery_attempts+1,delivery_next_attempt=NOW(3)+INTERVAL 30 SECOND WHERE id=? AND finished=0 AND delivery_status='PENDING' AND delivery_next_attempt<=NOW(3)",token,id)!=1)return;
  try{
   var task=service.getById((java.io.Serializable)id);if(task!=null)service.handleTask(task);
   jdbc.update("UPDATE notice_task SET delivery_attempts=0,delivery_error=NULL,delivery_token=NULL,delivery_next_attempt=NOW(3) WHERE id=? AND delivery_token=?",id,token);
  }catch(Exception error){int attempts=((Number)row.get("delivery_attempts")).intValue()+1;String reason=Objects.toString(error.getMessage(),"Notice failure");
   jdbc.update("UPDATE notice_task SET delivery_status=?,delivery_error=?,delivery_token=NULL,delivery_next_attempt=NOW(3)+INTERVAL ? SECOND WHERE id=? AND delivery_token=?",attempts>=10?"DEAD":"PENDING",reason.substring(0,Math.min(reason.length(),1000)),Math.min(300,1<<Math.min(attempts,8)),id,token);
  }
 }
}
