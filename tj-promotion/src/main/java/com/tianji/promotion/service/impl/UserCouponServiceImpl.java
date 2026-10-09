package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.autoconfigure.redisson.annotations.Lock;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.NumberUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tianji.promotion.strategy.discount.DiscountStrategy;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;

import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tianji.promotion.constants.PromotionConstants.COUPON_CODE_MAP_KEY;
import static com.tianji.promotion.constants.PromotionConstants.COUPON_RANGE_KEY;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-13
 */
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;

    private final IExchangeCodeService codeService;

    private final StringRedisTemplate redisTemplate;

    private final RabbitMqHelper mqHelper;
    private final com.tianji.common.autoconfigure.reliability.OperationStore operations;
    private final CouponClaimService claims;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final tools.jackson.databind.json.JsonMapper json;

    private static final RedisScript<Long> RECEIVE_COUPON_SCRIPT;
    private static final RedisScript<String> EXCHANGE_COUPON_SCRIPT;

    static {
        RECEIVE_COUPON_SCRIPT = RedisScript.of(new ClassPathResource("lua/receive_coupon.lua"), Long.class);
        EXCHANGE_COUPON_SCRIPT = RedisScript.of(new ClassPathResource("lua/exchange_coupon.lua"), String.class);
    }

    // 使用LUA脚本后无需加锁也是线程安全的
    @Override
    public void receiveCoupon(Long couponId) {
        operations.submit(UserContext.requireUser(),"COUPON_CLAIM",java.util.UUID.randomUUID().toString(),new CouponClaimService.Request(couponId,null));
    }
    @Override public void exchangeCoupon(String code) {
        operations.submit(UserContext.requireUser(),"COUPON_CLAIM",java.util.UUID.randomUUID().toString(),new CouponClaimService.Request(null,code));
    }
    @Transactional @Override public void checkAndCreateUserCoupon(UserCouponDTO uc) {
        String code=uc.getSerialNum()==null?null:jdbc.queryForObject("SELECT code FROM exchange_code WHERE id=?",String.class,uc.getSerialNum());
        claims.execute(java.util.UUID.randomUUID().toString(),uc.getUserId(),json.writeValueAsString(new CouponClaimService.Request(uc.getCouponId(),code)));
    }

    private Coupon queryCouponByCache(Long couponId) {
        // 1. 准备key
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        // 2. 查询
        Map<Object, Object> objectMap = redisTemplate.opsForHash().entries(key);
        if(objectMap.isEmpty()) {
            return null;
        }
        // 3. 数据反序列化
        return BeanUtils.mapToBean(objectMap, Coupon.class, false, CopyOptions.create());
    }

    @Override
    public PageDTO<CouponVO> queryMyCouponPage(UserCouponQuery query) {
        // 1. 获取当前登录用户
        Long userId = UserContext.getUser();

        // 2. 分页查询
        Page<UserCoupon> page = this.lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(query.getStatus() != null, UserCoupon::getStatus, query.getStatus())
                .page(query.toMpPage(OrderItem.asc("term_end_time")));
        List<UserCoupon> records = page.getRecords();
        if(CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 3. 获取优惠券的详细信息
        Set<Long> couponIds = records.stream().map(UserCoupon::getCouponId).collect(Collectors.toSet());
        List<Coupon> coupons = couponMapper.selectBatchIds(couponIds);

        // 4. 封装VO
        return PageDTO.of(page, BeanUtils.copyList(coupons, CouponVO.class));
    }

    @Override @Transactional public void writeOffCoupon(List<Long> ids) { changeUsage(ids,true); }
    @Override @Transactional public void refundCoupon(List<Long> ids) { changeUsage(ids,false); }
    private void changeUsage(List<Long> ids,boolean use) {
        if(ids==null || ids.isEmpty()) return;
        long user=UserContext.requireUser();
        for(Long id:ids.stream().distinct().sorted().toList()) {
            var rows=jdbc.queryForList("SELECT * FROM user_coupon WHERE id=? AND user_id=? FOR UPDATE",id,user);
            if(rows.isEmpty()) throw new BadRequestException("优惠券不存在");
            var row=rows.getFirst();int source=use?1:2;
            if(((Number)row.get("status")).intValue()!=source) continue;
            if(row.get("reserved_order_id")!=null) throw new BadRequestException("订单绑定券必须通过预占协议处理");
            int changed=use?jdbc.update("UPDATE user_coupon SET status=2,used_time=NOW() WHERE id=? AND status=1 AND term_begin_time<=NOW() AND term_end_time>NOW()",id)
                    :jdbc.update("UPDATE user_coupon SET status=IF(term_end_time>NOW(),1,3),used_time=NULL WHERE id=? AND status=2",id);
            if(changed==1 && couponMapper.incrUsedNum(List.of(((Number)row.get("coupon_id")).longValue()),use?1:-1)!=1) throw new DbException("优惠券统计不一致");
        }
    }

    @Override
    public List<String> queryDiscountRules(List<Long> userCouponIds) {
        if(CollUtils.isEmpty(userCouponIds))return List.of();
        if(userCouponIds.size()>6)throw new com.tianji.common.exceptions.BadRequestException("一次最多查询 6 张用户券");
        // 1.查询优惠券信息
        List<Coupon> coupons = baseMapper.queryCouponByUserCouponIds(userCouponIds, UserCouponStatus.USED, UserContext.getUser(),null);
        if (CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }
        // 2.转换规则
        return coupons.stream()
                .map(c -> DiscountStrategy.getDiscount(c.getDiscountType()).getRule(c))
                .collect(Collectors.toList());
    }

    private void saveUserCoupon(Long userId, Coupon coupon) {
        // 1. 基本信息
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());

        // 2. 有效信息
        LocalDateTime termBeginTime = coupon.getTermBeginTime();
        LocalDateTime termEndTime = coupon.getTermEndTime();
        if(termBeginTime == null) {
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
        }
        uc.setTermBeginTime(termBeginTime);
        uc.setTermEndTime(termEndTime);

        // 3. 保存
        save(uc);
    }
}
