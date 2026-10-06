package com.tianji.media.service.impl;
import com.tianji.common.autoconfigure.reliability.OperationHandler;
import com.tianji.common.exceptions.*;import com.tianji.common.utils.UserContext;
import com.tianji.media.storage.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;import org.springframework.beans.factory.annotation.Qualifier;import tools.jackson.databind.json.JsonMapper;import java.util.*;
@Service
public class StorageCleanupService implements OperationHandler{
 private final JdbcTemplate jdbc;private final JsonMapper json;private final IFileStorage files;private final IMediaStorage medias;private final ThreadPoolTaskExecutor executor;
 public StorageCleanupService(JdbcTemplate jdbc,JsonMapper json,IFileStorage files,IMediaStorage medias,@Qualifier("storageCleanupExecutor") ThreadPoolTaskExecutor executor){this.jdbc=jdbc;this.json=json;this.files=files;this.medias=medias;this.executor=executor;}
 public record Request(String kind,List<Long> ids,Long role){}
 public String kind(){return "RESOURCE_DELETE";}
 public Object execute(String operation,long user,String payload){
  Request request=json.readValue(payload,Request.class);
  if(request.ids()==null || request.ids().isEmpty() || request.ids().size()>100)throw new BadRequestException("每次删除 1 到 100 项");
  String table=request.kind().equals("MEDIA")?"media":request.kind().equals("FILE")?"file":null;if(table==null)throw new BadRequestException("资源类型无效");
  String column=table.equals("media")?"file_id":"`key`";
  for(Long id:request.ids().stream().distinct().sorted().toList()){
   var rows=jdbc.queryForList("SELECT creater,deleted,"+column+" object_key FROM "+table+" WHERE id=? FOR UPDATE",id);
   if(rows.isEmpty())continue;var row=rows.getFirst();
   if(!Long.valueOf(1).equals(request.role()) && !Objects.equals(((Number)row.get("creater")).longValue(),user))throw new ForbiddenException("不能删除他人的资源");
   if(((Number)row.get("deleted")).intValue()!=0)continue;
   jdbc.update("UPDATE "+table+" SET deleted=1 WHERE id=? AND deleted=0",id);
   jdbc.update("INSERT INTO storage_cleanup_task(id,kind,object_key) VALUES(?,?,?)",UUID.randomUUID().toString(),request.kind(),row.get("object_key"));
  }return Map.of("accepted",true);
 }
 @Scheduled(fixedDelay=1000) public void poll(){
  for(var row:jdbc.queryForList("SELECT * FROM storage_cleanup_task WHERE status='PENDING' AND next_attempt_at<=NOW(3) ORDER BY next_attempt_at LIMIT 20")){
   try{executor.execute(()->process(row));}catch(org.springframework.core.task.TaskRejectedException rejected){break;}
  }
 }
 private void process(Map<String,Object> row){
  String token=UUID.randomUUID().toString();Object id=row.get("id");
  if(jdbc.update("UPDATE storage_cleanup_task SET lease_token=?,attempts=attempts+1,next_attempt_at=NOW(3)+INTERVAL 30 SECOND WHERE id=? AND status='PENDING' AND next_attempt_at<=NOW(3)",token,id)!=1)return;
  try{
   if("MEDIA".equals(row.get("kind")))medias.deleteFile(row.get("object_key").toString());else files.deleteFile(row.get("object_key").toString());
   jdbc.update("UPDATE storage_cleanup_task SET status='DONE',lease_token=NULL WHERE id=? AND lease_token=?",id,token);
  }catch(Exception error){int attempts=((Number)row.get("attempts")).intValue()+1;String message=Objects.toString(error.getMessage(),"Storage deletion failed");
   jdbc.update("UPDATE storage_cleanup_task SET status=?,last_error=?,lease_token=NULL,next_attempt_at=NOW(3)+INTERVAL ? SECOND WHERE id=? AND lease_token=?",attempts>=10?"DEAD":"PENDING",message.substring(0,Math.min(message.length(),1000)),Math.min(300,1<<Math.min(attempts,8)),id,token);
  }
 }
}
