package com.tianji.trade.controller;
import com.tianji.common.utils.UserContext;
import com.tianji.common.exceptions.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v2/admin/compensations")
public class CompensationController {
 private final JdbcTemplate jdbc;
 private String table(String kind){return switch(kind){case "refund"->"refund_delivery_task";case "payment"->"payment_reconcile";default->throw new BadRequestException("补偿类型无效");};}
 @GetMapping("/{kind}") public List<Map<String,Object>> list(@PathVariable String kind){UserContext.requireAdmin();return jdbc.queryForList("SELECT * FROM "+table(kind)+" WHERE status<>'DONE' ORDER BY next_attempt_at LIMIT 100");}
 @PostMapping("/{kind}/{id}/replay") public void replay(@PathVariable String kind,@PathVariable long id,@RequestParam long version){
  UserContext.requireAdmin();String table=table(kind),column=kind.equals("refund")?"refund_id":"order_id";
  if(jdbc.update("UPDATE "+table+" SET status='PENDING',attempts=0,last_error=NULL,next_attempt_at=NOW(),version=version+1"+(kind.equals("payment")?",expires_at=NOW()+INTERVAL 1 DAY":"")+" WHERE "+column+"=? AND status='DEAD' AND version=?",id,version)!=1)throw new ConflictException("任务已变化或不是失败状态");
 }
}
