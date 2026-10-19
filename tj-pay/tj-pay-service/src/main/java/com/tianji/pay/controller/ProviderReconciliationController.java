package com.tianji.pay.controller;

import com.tianji.common.exceptions.*;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v2/admin/provider-payments")
public class ProviderReconciliationController {
    private final JdbcTemplate jdbc;
    public record Resolution(long version,String resolution) {}
    private void validate(Resolution form) {
        if(form==null || form.resolution()==null || form.resolution().isBlank() || form.resolution().length()>2000)
            throw new BadRequestException("请记录人工核对依据，最多2000字");
    }
    @GetMapping("/anomalies") public List<Map<String,Object>> anomalies(@RequestParam(defaultValue="OPEN") String status) {
        UserContext.requireAdmin();
        if(!Set.of("OPEN","RESOLVED").contains(status))throw new BadRequestException("异常状态无效");
        return jdbc.queryForList("SELECT * FROM provider_payment_anomaly WHERE status=? ORDER BY last_seen_at DESC LIMIT 100",status);
    }
    @PutMapping("/anomalies/{number}/{reason}") public void resolve(@PathVariable long number,@PathVariable String reason,@RequestBody Resolution form) {
        UserContext.requireAdmin();validate(form);
        if(jdbc.update("UPDATE provider_payment_anomaly SET status='RESOLVED',version=version+1,resolution=?,resolved_by=?,resolved_at=CURRENT_TIMESTAMP(3) WHERE pay_order_no=? AND reason=? AND status='OPEN' AND version=?",form.resolution(),UserContext.getUser(),number,reason,form.version())!=1)
            throw new ConflictException("异常已更新，请重新核对");
    }
    @GetMapping("/reconciliations") public List<Map<String,Object>> tasks(@RequestParam(defaultValue="DEAD") String status) {
        UserContext.requireAdmin();
        if(!Set.of("PENDING","SENDING","DONE","DEAD").contains(status))throw new BadRequestException("核对任务状态无效");
        return jdbc.queryForList("SELECT * FROM provider_payment_reconcile WHERE status=? ORDER BY next_attempt_at,pay_order_no LIMIT 100",status);
    }
    @PostMapping("/reconciliations/{number}/replay") public void replay(@PathVariable long number,@RequestBody Resolution form) {
        UserContext.requireAdmin();validate(form);
        if(jdbc.update("UPDATE provider_payment_reconcile SET status='PENDING',attempts=0,lease_token=NULL,next_attempt_at=CURRENT_TIMESTAMP(3),expires_at=CURRENT_TIMESTAMP(3)+INTERVAL 7 DAY,last_error=NULL,replay_reason=?,replayed_by=?,replayed_at=CURRENT_TIMESTAMP(3),version=version+1 WHERE pay_order_no=? AND status='DEAD' AND version=?",form.resolution(),UserContext.getUser(),number,form.version())!=1)
            throw new ConflictException("任务已更新或不是待人工核对状态");
    }
}
