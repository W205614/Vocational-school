package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.framework.AopContext;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final PropertyPlaceholderAutoConfiguration propertyPlaceholderAutoConfiguration;

    @Override
    public void receiveCoupon(Long couponId) {
        // 1. 查询优惠卷
        Coupon coupon = couponMapper.selectById(couponId);
        if(coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        // 2. 检验发放时间
        LocalDateTime now = LocalDateTime.now();
        if(now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
            throw new BadRequestException("优惠券发放已经结束或尚未开始");
        }
        // 3. 校验库存
        if(coupon.getIssueNum() >= coupon.getTotalNum()) {
            throw new BadRequestException("优惠券库存不足");
        }
        Long userId = UserContext.getUser();
        // 4. 校验并生成用户券
        synchronized (userId.toString().intern()) {
            IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
            userCouponService.checkAndCreateUserCoupon(userId, coupon);
        }
    }

    @Transactional
    @Override
    public void checkAndCreateUserCoupon(Long userId, Coupon coupon) {
        // 1. 校验每人限领数量
        // 1.1 统计当前用户对当前优惠券的已经领取的数量
        Integer count = lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, coupon.getId())
                .count();
        // 1.2 判断
        if(count == null || count >= coupon.getUserLimit()) {
            throw new BadRequestException("领取次数太多");
        }
        // 2. 更新优惠卷的已发放数量 + 1
        int r = couponMapper.incrIssueNum(coupon.getId());
        if(r == 0) {
            throw new BizIllegalException("优惠券库存不足! ");
        }
        // 3. 新增一个用户券
        saveUserCoupon(userId, coupon);
    }

    @Override
    public void exchangeCoupon(String code) {
        // 1. 校验并解析兑换码
        long serialNum = CodeUtil.parseCode(code);
        // 2. 校验是否已经兑换
        boolean exchanged = codeService.updateExchangeMark(serialNum, true);
        if(exchanged) {
            throw new BizIllegalException("兑换码已经被兑换过了");
        }
        try {
            // 3. 查询兑换码
            ExchangeCode exchangeCode = codeService.getById(serialNum);
            if(exchangeCode == null) {
                throw new BizIllegalException("兑换码不存在");
            }
            // 4. 是否过期
            LocalDateTime now = LocalDateTime.now();
            if(now.isAfter(exchangeCode.getExpiredTime())) {
                throw new BizIllegalException("兑换码已经过期");
            }
            // 5. 校验并生成用户券
            // 5.1 查询优惠券
            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
            // 5.2 获取用户
            Long userId = UserContext.getUser();
            synchronized (userId.toString().intern()) {
                IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
                userCouponService.checkAndCreateUserCoupon(userId, coupon);
            }
            // 6. 更新兑换码状态
            codeService.lambdaUpdate()
                    .set(ExchangeCode::getUserId, userId)
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .eq(ExchangeCode::getId, exchangeCode.getId())
                    .update();
        } catch (Exception e) {
            // 重置兑换的标记
            codeService.updateExchangeMark(serialNum, false);
            throw e;
        }
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
