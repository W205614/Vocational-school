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
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.strategy.discount.DiscountStrategy;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.units.qual.C;
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

    private static final RedisScript<Long> RECEIVE_COUPON_SCRIPT;
    private static final RedisScript<String> EXCHANGE_COUPON_SCRIPT;

    static {
        RECEIVE_COUPON_SCRIPT = RedisScript.of(new ClassPathResource("lua/receive_coupon.lua"), Long.class);
        EXCHANGE_COUPON_SCRIPT = RedisScript.of(new ClassPathResource("lua/exchange_coupon.lua"), String.class);
    }

    // 使用LUA脚本后无需加锁也是线程安全的
    @Override
    public void receiveCoupon(Long couponId) {
        // 1.执行LUA脚本，判断结果
        // 1.1.准备参数
        String key1 = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        String key2 = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
        Long userId = UserContext.getUser();
        // 1.2.执行脚本
        Long r = redisTemplate.execute(RECEIVE_COUPON_SCRIPT, List.of(key1, key2), userId.toString());
        int result = NumberUtils.null2Zero(r).intValue();
        if (result != 0) {
            // 结果大于0，说明出现异常
            throw new BizIllegalException(PromotionConstants.RECEIVE_COUPON_ERROR_MSG[result - 1]);
        }
        // 2.发送MQ消息
        UserCouponDTO uc = new UserCouponDTO();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE, uc);
    }

    // 使用LUA脚本后无需加锁也是线程安全的
    @Override
    public void exchangeCoupon(String code) {
        // 1.校验并解析兑换码
        long serialNum = CodeUtil.parseCode(code);
        // 2.执行LUA脚本
        Long userId = UserContext.getUser();
        String result = redisTemplate.execute(
                EXCHANGE_COUPON_SCRIPT,
                List.of(COUPON_CODE_MAP_KEY, COUPON_RANGE_KEY),
                String.valueOf(serialNum), String.valueOf(serialNum + 5000), userId.toString());
        long r = NumberUtils.parseLong(result);
        if (r < 10) {
            // 异常结果应该是在1~5之间
            throw new BizIllegalException(PromotionConstants.EXCHANGE_COUPON_ERROR_MSG[(int) (r - 1)]);
        }
        // 3.发送MQ消息通知
        UserCouponDTO uc = new UserCouponDTO();
        uc.setUserId(userId);
        uc.setCouponId(r);
        uc.setSerialNum((int) serialNum);
        mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE, uc);
    }

    @Transactional
    @Override
    public void checkAndCreateUserCoupon(UserCouponDTO uc) {
        // 1.查询优惠券
        Coupon coupon = couponMapper.selectById(uc.getCouponId());
        if (coupon == null) {
            throw new BizIllegalException("优惠券不存在！");
        }
        // 2.更新优惠券的已经发放的数量 + 1
        int r = couponMapper.incrIssueNum(coupon.getId());
        if (r == 0) {
            throw new BizIllegalException("优惠券库存不足！");
        }
        // 3.新增一个用户券
        saveUserCoupon(uc.getUserId(), coupon);

        // 4.更新兑换码状态
        if (uc.getSerialNum() != null) {
            codeService.lambdaUpdate()
                    .set(ExchangeCode::getUserId, uc.getUserId())
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .eq(ExchangeCode::getId, uc.getSerialNum())
                    .update();
        }
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
                .eq(UserCoupon::getStatus, query.getStatus())
                .page(query.toMpPage(new OrderItem("term_end_time", true)));
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

    @Override
    @Transactional
    public void writeOffCoupon(List<Long> userCouponIds) {
        // 1.查询优惠券
        List<UserCoupon> userCoupons = listByIds(userCouponIds);
        if (CollUtils.isEmpty(userCoupons)) {
            return;
        }
        // 2.处理数据
        List<UserCoupon> list = userCoupons.stream()
                // 过滤无效券
                .filter(coupon -> {
                    if (coupon == null) {
                        return false;
                    }
                    if (UserCouponStatus.UNUSED != coupon.getStatus()) {
                        return false;
                    }
                    LocalDateTime now = LocalDateTime.now();
                    return !now.isBefore(coupon.getTermBeginTime()) && !now.isAfter(coupon.getTermEndTime());
                })
                // 组织新增数据
                .map(coupon -> {
                    UserCoupon c = new UserCoupon();
                    c.setId(coupon.getId());
                    c.setStatus(UserCouponStatus.USED);
                    return c;
                })
                .collect(Collectors.toList());

        // 4.核销，修改优惠券状态
        boolean success = updateBatchById(list);
        if (!success) {
            return;
        }
        // 5.更新已使用数量
        List<Long> couponIds = userCoupons.stream().map(UserCoupon::getCouponId).collect(Collectors.toList());
        int c = couponMapper.incrUsedNum(couponIds, 1);
        if (c < 1) {
            throw new DbException("更新优惠券使用数量失败！");
        }
    }

    @Override
    @Transactional
    public void refundCoupon(List<Long> userCouponIds) {
        // 1.查询优惠券
        List<UserCoupon> userCoupons = listByIds(userCouponIds);
        if (CollUtils.isEmpty(userCoupons)) {
            return;
        }
        // 2.处理优惠券数据
        List<UserCoupon> list = userCoupons.stream()
                // 过滤无效券
                .filter(coupon -> coupon != null && UserCouponStatus.USED == coupon.getStatus())
                // 更新状态字段
                .map(coupon -> {
                    UserCoupon c = new UserCoupon();
                    c.setId(coupon.getId());
                    // 3.判断有效期，是否已经过期，如果过期，则状态为 已过期，否则状态为 未使用
                    LocalDateTime now = LocalDateTime.now();
                    UserCouponStatus status = now.isAfter(coupon.getTermEndTime()) ?
                            UserCouponStatus.EXPIRED : UserCouponStatus.UNUSED;
                    c.setStatus(status);
                    return c;
                }).collect(Collectors.toList());

        // 4.修改优惠券状态
        boolean success = updateBatchById(list);
        if (!success) {
            return;
        }
        // 5.更新已使用数量
        List<Long> couponIds = userCoupons.stream().map(UserCoupon::getCouponId).collect(Collectors.toList());
        int c = couponMapper.incrUsedNum(couponIds, -1);
        if (c < 1) {
            throw new DbException("更新优惠券使用数量失败！");
        }
    }

    @Override
    public List<String> queryDiscountRules(List<Long> userCouponIds) {
        // 1.查询优惠券信息
        List<Coupon> coupons = baseMapper.queryCouponByUserCouponIds(userCouponIds, UserCouponStatus.USED);
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
