package com.tianji.trade.controller;
import com.tianji.common.utils.UserContext;
import com.tianji.common.exceptions.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/admin/payment-conflicts")
public class PaymentConflictController {
    private final JdbcTemplate jdbc;
    public record Resolution(long version,String resolution) {}
    @GetMapping public List<Map<String,Object>> list(@RequestParam(defaultValue="OPEN") String status) {
        UserContext.requireAdmin();
        return jdbc.queryForList("SELECT * FROM payment_conflict WHERE status=? ORDER BY created_at DESC LIMIT 100",status);
    }
    @PutMapping("/{id}") public void resolve(@PathVariable long id,@RequestBody Resolution form) {
        UserContext.requireAdmin();
        if(form.resolution()==null || form.resolution().isBlank() || form.resolution().length()>2000) throw new BadRequestException("请记录人工处理依据");
        if(jdbc.update("UPDATE payment_conflict SET status='RESOLVED',resolution=?,resolved_by=?,resolved_at=CURRENT_TIMESTAMP(3),version=version+1 WHERE id=? AND status='OPEN' AND version=?",
                form.resolution(),UserContext.getUser(),id,form.version())!=1) throw new ConflictException("工单已处理或版本已变化");
    }
}
