package com.tianji.message.service.impl;
import com.tianji.api.dto.sms.SmsInfoDTO;import com.tianji.common.autoconfigure.reliability.*;import com.tianji.common.utils.UserContext;import com.tianji.message.service.ISmsService;
import org.springframework.stereotype.Service;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.core.env.Environment;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;import org.springframework.beans.factory.annotation.Qualifier;import org.springframework.beans.factory.ObjectProvider;import tools.jackson.databind.json.JsonMapper;import java.util.*;
@Service
public class SmsDeliveryTasks implements OperationHandler{
 private final JdbcTemplate jdbc;private final JsonMapper json;private final ObjectProvider<ISmsService> service;private final ThreadPoolTaskExecutor executor;private final boolean simulated;
 public SmsDeliveryTasks(JdbcTemplate jdbc,JsonMapper json,ObjectProvider<ISmsService> service,@Qualifier("asyncSmsExecutor") ThreadPoolTaskExecutor executor,Environment environment){
  this.jdbc=jdbc;this.json=json;this.service=service;this.executor=executor;simulated=environment.getProperty("tj.sms.simulated",Boolean.class,false);
  if(simulated && Arrays.stream(environment.getActiveProfiles()).noneMatch(Set.of("acceptance","local-simulator")::contains))throw new IllegalStateException("Simulated SMS forbidden in real environment");
 }
 public void stage(String id,SmsInfoDTO message){
  if(message==null || message.getPhones()==null || message.getTemplateCode()==null)throw new com.tianji.common.exceptions.BadRequestException("短信模板或接收人无效");
  var phones=java.util.stream.StreamSupport.stream(message.getPhones().spliterator(),false).limit(101).toList();
  if(phones.isEmpty() || phones.size()>100 || phones.stream().anyMatch(p->p==null || !p.matches("[0-9+]{6,20}")))throw new com.tianji.common.exceptions.BadRequestException("短信接收人无效");
  jdbc.update("INSERT IGNORE INTO sms_delivery_task(id,payload) VALUES(?,?)",id,json.writeValueAsString(message));
 }
 public String kind(){return "SMS_SEND";}
 public Object execute(String operation,long user,String payload){stage(operation,json.readValue(payload,SmsInfoDTO.class));return Map.of("deliveryId",operation,"mode",simulated?"SIMULATED":"REAL");}
 @Scheduled(fixedDelay=1000) public void poll(){
  for(var row:jdbc.queryForList("SELECT * FROM sms_delivery_task WHERE status='PENDING' AND next_attempt_at<=NOW(3) ORDER BY next_attempt_at LIMIT 20")){
   try{executor.execute(()->process(row));}catch(org.springframework.core.task.TaskRejectedException rejected){break;}
  }
 }
 private void process(Map<String,Object> row){
  String token=UUID.randomUUID().toString();Object id=row.get("id");
  if(jdbc.update("UPDATE sms_delivery_task SET lease_token=?,attempts=attempts+1,next_attempt_at=NOW(3)+INTERVAL 60 SECOND WHERE id=? AND status='PENDING' AND next_attempt_at<=NOW(3)",token,id)!=1)return;
  try{
   if(simulated)jdbc.update("INSERT IGNORE INTO simulated_sms(task_id,payload) VALUES(?,?)",id,row.get("payload"));
   else service.getObject().sendMessage(json.readValue(row.get("payload").toString(),SmsInfoDTO.class));
   jdbc.update("UPDATE sms_delivery_task SET status='DONE',lease_token=NULL WHERE id=? AND lease_token=?",id,token);
  }catch(Exception failure){int attempts=((Number)row.get("attempts")).intValue()+1;String error=Objects.toString(failure.getMessage(),"SMS failure");
   jdbc.update("UPDATE sms_delivery_task SET status=?,last_error=?,lease_token=NULL,next_attempt_at=NOW(3)+INTERVAL ? SECOND WHERE id=? AND lease_token=?",attempts>=10?"DEAD":"PENDING",error.substring(0,Math.min(error.length(),1000)),Math.min(300,1<<Math.min(attempts,8)),id,token);
  }
 }
}
