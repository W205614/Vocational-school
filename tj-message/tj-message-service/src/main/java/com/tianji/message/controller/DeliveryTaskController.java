package com.tianji.message.controller;
import com.tianji.common.utils.UserContext;import com.tianji.common.exceptions.*;import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;import org.springframework.jdbc.core.JdbcTemplate;import java.util.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v2/admin/deliveries")
public class DeliveryTaskController{
 private final JdbcTemplate jdbc;
 @GetMapping("/{kind}") public Object list(@PathVariable String kind){UserContext.requireAdmin();return switch(kind){
  case "sms"->jdbc.queryForList("SELECT id,status,attempts,last_error,version,next_attempt_at FROM sms_delivery_task WHERE status<>'DONE' ORDER BY next_attempt_at LIMIT 100");
  case "notice"->jdbc.queryForList("SELECT id,delivery_status status,delivery_attempts attempts,delivery_error last_error,delivery_version version,delivery_next_attempt next_attempt_at FROM notice_task WHERE finished=0 ORDER BY delivery_next_attempt LIMIT 100");
  default->throw new BadRequestException("通知任务类型无效");
 };}
 @PostMapping("/{kind}/{id}/replay") public void replay(@PathVariable String kind,@PathVariable String id,@RequestParam long version){
  UserContext.requireAdmin();int changed=switch(kind){
   case "sms"->jdbc.update("UPDATE sms_delivery_task SET status='PENDING',attempts=0,last_error=NULL,next_attempt_at=NOW(3),version=version+1 WHERE id=? AND status='DEAD' AND version=?",id,version);
   case "notice"->jdbc.update("UPDATE notice_task SET delivery_status='PENDING',delivery_attempts=0,delivery_error=NULL,delivery_next_attempt=NOW(3),delivery_version=delivery_version+1 WHERE id=? AND delivery_status='DEAD' AND delivery_version=?",id,version);
   default->throw new BadRequestException("通知任务类型无效");
  };if(changed!=1)throw new ConflictException("任务版本已变化或任务尚未失败");
 }
}
