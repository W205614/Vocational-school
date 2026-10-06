package com.tianji.media.controller;
import com.tianji.common.utils.UserContext;
import com.tianji.common.exceptions.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v2/admin/storage-cleanups")
public class StorageCleanupController {
 private final JdbcTemplate jdbc;
 @GetMapping public Object list() {
  UserContext.requireAdmin();
  return jdbc.queryForList("SELECT id,kind,status,attempts,last_error,next_attempt_at,version FROM storage_cleanup_task WHERE status<>'DONE' ORDER BY next_attempt_at LIMIT 100");
 }
 @PostMapping("/{id}/replay") public void replay(@PathVariable String id,@RequestParam long version) {
  UserContext.requireAdmin();
  if(jdbc.update("UPDATE storage_cleanup_task SET status='PENDING',attempts=0,last_error=NULL,lease_token=NULL,next_attempt_at=NOW(3),version=version+1 WHERE id=? AND status='DEAD' AND version=?",id,version)!=1)
   throw new ConflictException("任务版本已变化或任务尚未失败");
 }
}
