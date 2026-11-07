package com.zhixuetang.promotion.service.impl;

import com.zhixuetang.promotion.domain.po.CouponScope;
import com.zhixuetang.promotion.mapper.CouponScopeMapper;
import com.zhixuetang.promotion.service.ICouponScopeService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 优惠券作用范围信息 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-11
 */
@Service
public class CouponScopeServiceImpl extends ServiceImpl<CouponScopeMapper, CouponScope>
        implements ICouponScopeService {}
