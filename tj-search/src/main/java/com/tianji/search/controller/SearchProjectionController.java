package com.tianji.search.controller;
import com.tianji.common.utils.UserContext;
import com.tianji.common.exceptions.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v2/admin/search-projections")
public class SearchProjectionController {
 private final JdbcTemplate jdbc;
 @GetMapping public Object list(){
  UserContext.requireAdmin();
  return jdbc.queryForList("SELECT course_id,status,attempts,last_error,version,processed_version,next_attempt_at,'metadata' AS kind FROM course_metadata_projection WHERE version>processed_version UNION ALL SELECT course_id,status,attempts,last_error,version,processed_version,next_attempt_at,'sales' AS kind FROM course_sales_projection WHERE version>processed_version ORDER BY next_attempt_at LIMIT 100");
 }
 @PostMapping("/{id}/replay") public void replay(@PathVariable long id,@RequestParam long version,@RequestParam(defaultValue="metadata") String kind){
  UserContext.requireAdmin();
  String table=switch(kind){case "metadata"->"course_metadata_projection";case "sales"->"course_sales_projection";default->throw new com.tianji.common.exceptions.BadRequestException("投影类型无效");};
  if(jdbc.update("UPDATE "+table+" SET status='PENDING',attempts=0,last_error=NULL,lease_token=NULL,lease_until=NULL,next_attempt_at=NOW(3),version=version+1 WHERE course_id=? AND status='DEAD' AND version=?",id,version)!=1)throw new ConflictException("投影任务版本已变化或任务尚未失败");
 }
}
