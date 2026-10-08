package com.tianji.trade.controller;
import com.tianji.api.dto.trade.OrderEntitlementDTO;
import com.tianji.common.exceptions.ConflictException;
import com.tianji.common.utils.InternalAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequiredArgsConstructor
public class InternalEntitlementController {
 private final JdbcTemplate jdbc;
 @GetMapping("/internal/learning-entitlements/{orderId}")
 public List<OrderEntitlementDTO> entitlements(@PathVariable long orderId) {
  InternalAuth.requireService();
  return jdbc.query("SELECT d.id,d.user_id,d.course_id,d.valid_duration,d.course_expire_time,d.status,d.refund_status,COALESCE(o.pay_time,o.finish_time) purchased_at FROM order_detail d JOIN `order` o ON o.id=d.order_id WHERE d.order_id=? ORDER BY d.course_id",
   (row,index)->{
    if(row.getTimestamp("purchased_at")==null || !Set.of(2,4,5,6,7).contains(row.getInt("status"))) throw new ConflictException("订单尚无付款或报名事实");
    Integer duration=(Integer)row.getObject("valid_duration");
    if((duration==null || duration<=0) && row.getTimestamp("course_expire_time")!=null) throw new ConflictException("订单明细有效期事实冲突，需要核对历史记录");
    return new OrderEntitlementDTO(orderId,row.getLong("id"),row.getLong("user_id"),row.getLong("course_id"),row.getInt("valid_duration"),row.getTimestamp("purchased_at").toLocalDateTime(),row.getInt("refund_status")==5 || row.getInt("status")==7);
   },orderId);
 }
}
