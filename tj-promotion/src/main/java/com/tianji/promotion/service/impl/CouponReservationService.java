package com.tianji.promotion.service.impl;
import com.tianji.api.dto.promotion.CouponReservationDTO;
import com.tianji.common.exceptions.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
@Service @RequiredArgsConstructor
public class CouponReservationService {
    private final JdbcTemplate jdbc; private final JsonMapper json;
    @Transactional public Map<String,Object> reserve(long order,CouponReservationDTO request) {
        if(request.userId()==null || request.couponIds()==null || request.couponIds().isEmpty() || request.couponIds().size()>6 ||
                new HashSet<>(request.couponIds()).size()!=request.couponIds().size() || request.expiresAt()==null || request.expiresAt().isBefore(java.time.LocalDateTime.now()))
            throw new BadRequestException("券预占参数无效");
        List<Long> ids=request.couponIds().stream().sorted().toList();
        String payload=json.writeValueAsString(ids);
        String hash=cn.hutool.crypto.digest.DigestUtil.sha256Hex(request.userId()+":"+payload);
        jdbc.update("INSERT INTO coupon_reservation(order_id,user_id,coupon_ids,request_hash,status,expires_at) VALUES(?,?,?,?,'CREATING',?) ON DUPLICATE KEY UPDATE order_id=order_id",order,request.userId(),payload,hash,request.expiresAt());
        Map<String,Object> existing=jdbc.queryForMap("SELECT * FROM coupon_reservation WHERE order_id=? FOR UPDATE",order);
        if(!hash.equals(existing.get("request_hash"))) throw new ConflictException("订单已预占不同的优惠券");
        if(!"CREATING".equals(existing.get("status"))) {
            if("RELEASED".equals(existing.get("status"))) throw new ConflictException("订单的券预占已释放");
            return existing;
        }
        for(Long id:ids) {
            if(jdbc.update("UPDATE user_coupon SET reserved_order_id=? WHERE id=? AND user_id=? AND status=1 AND reserved_order_id IS NULL AND term_begin_time<=NOW() AND term_end_time>NOW()",order,id,request.userId())!=1)
                throw new ConflictException("优惠券已被预占、已使用或不属于该用户");
        }
        jdbc.update("UPDATE coupon_reservation SET status='HELD' WHERE order_id=?",order);
        return jdbc.queryForMap("SELECT * FROM coupon_reservation WHERE order_id=?",order);
    }
    @Transactional public void transition(long order,long user,boolean confirm) {
        if(!confirm) jdbc.update("INSERT INTO coupon_reservation(order_id,user_id,coupon_ids,request_hash,status,expires_at) VALUES(?,?,'[]',?,'RELEASED',CURRENT_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE order_id=order_id",order,user,cn.hutool.crypto.digest.DigestUtil.sha256Hex(user+":[]"));
        var rows=jdbc.queryForList("SELECT * FROM coupon_reservation WHERE order_id=? AND user_id=? FOR UPDATE",order,user);
        if(rows.isEmpty()) {
            if(!confirm) jdbc.update("INSERT IGNORE INTO coupon_reservation(order_id,user_id,coupon_ids,request_hash,status,expires_at) VALUES(?,?,'[]',?,'RELEASED',CURRENT_TIMESTAMP(3))",
                    order,user,cn.hutool.crypto.digest.DigestUtil.sha256Hex(user+":[]"));
            return;
        }
        var reservation=rows.getFirst();String state=reservation.get("status").toString();
        String target=confirm?"CONFIRMED":"RELEASED";
        if(target.equals(state)) return;
        if(!"HELD".equals(state)) throw new ConflictException("券预占状态不允许该转换");
        Long[] ids=json.readValue(reservation.get("coupon_ids").toString(),Long[].class);
        for(Long id:ids) {
            var coupon=jdbc.queryForMap("SELECT coupon_id,status,reserved_order_id FROM user_coupon WHERE id=? FOR UPDATE",id);
            if(!Objects.equals(((Number)coupon.get("reserved_order_id")).longValue(),order)) throw new ConflictException("券预占关系不一致");
            if(confirm) {
                if(jdbc.update("UPDATE user_coupon SET status=2,used_time=NOW() WHERE id=? AND status=1 AND reserved_order_id=?",id,order)!=1) throw new ConflictException("优惠券状态不一致");
                if(jdbc.update("UPDATE coupon SET used_num=used_num+1 WHERE id=? AND used_num<issue_num",coupon.get("coupon_id"))!=1) throw new IllegalStateException("Coupon usage count mismatch");
            } else jdbc.update("UPDATE user_coupon SET reserved_order_id=NULL WHERE id=? AND status=1 AND reserved_order_id=?",id,order);
        }
        jdbc.update("UPDATE coupon_reservation SET status=? WHERE order_id=?",target,order);
    }
}
