package com.tianji.trade.controller;
import com.tianji.common.utils.UserContext;import lombok.RequiredArgsConstructor;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v2/admin/dashboard")
public class DashboardController {
 private final JdbcTemplate jdbc;
 @GetMapping public Object dashboard(){
  UserContext.requireAdmin();
  var result=new LinkedHashMap<String,Object>();
  result.put("createdOrders",jdbc.queryForObject("SELECT COUNT(*) FROM `order` WHERE create_time>=CURDATE()",Long.class));
  result.put("paidAmount",jdbc.queryForObject("SELECT COALESCE(SUM(real_amount),0) FROM `order` WHERE finish_time>=CURDATE() AND status IN(2,5,6)",Long.class));
  result.put("refundAmount",jdbc.queryForObject("SELECT COALESCE(SUM(refund_amount),0) FROM refund_apply WHERE finish_time>=CURDATE() AND status=5",Long.class));
  result.put("openConflicts",jdbc.queryForObject("SELECT COUNT(*) FROM payment_conflict WHERE status='OPEN'",Long.class));
  result.put("pendingEvents",jdbc.queryForObject("SELECT COUNT(*) FROM reliability_outbox WHERE status IN('PENDING','SENDING')",Long.class));
  result.put("orderDays",jdbc.queryForList("SELECT DATE(create_time) day,COUNT(*) orders,COALESCE(SUM(IF(status IN(2,5,6),real_amount,0)),0) paid_amount FROM `order` WHERE create_time>=CURDATE()-INTERVAL 29 DAY GROUP BY DATE(create_time) ORDER BY day"));
  result.put("updatedAt",java.time.OffsetDateTime.now());return result;
 }
}
