package com.tianji.promotion.service.impl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.common.autoconfigure.reliability.*;
import com.tianji.common.exceptions.*;
import com.tianji.promotion.domain.po.*;
import com.tianji.promotion.mapper.*;
import com.tianji.promotion.enums.*;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CouponClaimService implements OperationHandler {
    private static final java.util.concurrent.Semaphore[] CLAIMS=java.util.stream.IntStream.range(0,128).mapToObj(i->new java.util.concurrent.Semaphore(2)).toArray(java.util.concurrent.Semaphore[]::new);
    private final CouponMapper coupons;
    private final UserCouponMapper userCoupons;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    @Override public String kind() { return "COUPON_CLAIM"; }
    public record Request(Long couponId,String code) {}
    @Override public Object execute(String id,long user,String payload) {
        Request request=json.readValue(payload,Request.class);
        Long couponId=request.couponId();
        Map<String,Object> code=null;
        if(request.code()!=null) {
            long serial=CodeUtil.parseCode(request.code());
            var rows=jdbc.queryForList("SELECT * FROM exchange_code WHERE id=? AND code=? AND status=1 AND expired_time>NOW()",serial,request.code());
            if(rows.isEmpty()) throw new BizIllegalException("兑换码无效或已使用");
            code=rows.getFirst();couponId=((Number)code.get("exchange_target_id")).longValue();
        }
        if(couponId==null) throw new BadRequestException("优惠券标识不能为空");
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())throw new IllegalStateException("Coupon claims require their module transaction");
        var permit=CLAIMS[Math.floorMod(Long.hashCode(couponId),CLAIMS.length)];
        if(!permit.tryAcquire())throw new ServiceUnavailableException("该优惠券处理繁忙，将自动重试");
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCompletion(int status){permit.release();}});
        // Serializing on the template row protects both global stock and per-user limit.
        Coupon coupon=coupons.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Coupon>().eq("id",couponId).last("FOR UPDATE"));
        LocalDateTime now=LocalDateTime.now();
        if(coupon==null) throw new BizIllegalException("优惠券不存在");
        if(code==null && (coupon.getObtainWay()!=ObtainType.PUBLIC || coupon.getStatus()!=CouponStatus.ISSUING || now.isBefore(coupon.getIssueBeginTime()) || !now.isBefore(coupon.getIssueEndTime())))
            throw new BizIllegalException("不在优惠券领取时间内");
        // The exchange-code lookup may already have established an old snapshot.
        // Read current rows after acquiring the template lock for the user limit.
        long count=jdbc.queryForList("SELECT id FROM user_coupon WHERE user_id=? AND coupon_id=? FOR UPDATE",user,couponId).size();
        if(count>=coupon.getUserLimit()) throw new BizIllegalException("超过个人领取上限");
        if(coupons.incrIssueNum(couponId)!=1) throw new BizIllegalException("优惠券库存不足");
        if(code!=null && jdbc.update("UPDATE exchange_code SET status=2,user_id=? WHERE id=? AND status=1 AND expired_time>NOW()",user,code.get("id"))!=1)
            throw new BizIllegalException("兑换码已使用或过期");
        UserCoupon uc=new UserCoupon();uc.setId(IdWorker.getId());uc.setUserId(user);uc.setCouponId(couponId);
        LocalDateTime begin=coupon.getTermBeginTime()==null?now:coupon.getTermBeginTime();
        LocalDateTime end=coupon.getTermBeginTime()==null?now.plusDays(coupon.getTermDays()):coupon.getTermEndTime();
        if(end==null || !end.isAfter(now)) throw new BizIllegalException("优惠券已过期");
        uc.setTermBeginTime(begin);uc.setTermEndTime(end);uc.setStatus(UserCouponStatus.UNUSED);userCoupons.insert(uc);
        jdbc.update("UPDATE user_coupon SET claim_operation_id=? WHERE id=?",id,uc.getId());
        return Map.of("userCouponId",uc.getId(),"couponId",couponId);
    }
}
