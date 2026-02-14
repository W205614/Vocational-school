package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponScopeVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import io.prometheus.client.Collector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-11
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final ICouponScopeService scopeService;
    private final IUserCouponService userCouponService;
    private final IExchangeCodeService codeService;
    private final CategoryCache categoryCache;

    @Override
    @Transactional
    public void saveCoupon(CouponFormDTO dto) {
        // 1. 保存优惠卷
        // 1.1 转po
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        // 1.2 保存
        save(coupon);

        if(!dto.getSpecific()) {
            // 没有限定范围
            return;
        }
        Long couponId = coupon.getId();
        // 2. 保存限定范围
        List<Long> scopes = dto.getScopes();
        if(CollUtils.isEmpty(scopes)) {
            throw new BadRequestException("限定范围不能为空");
        }
        // 2.1 转换po
        List<CouponScope> list = scopes.stream()
                .map(bizId -> new CouponScope().setBizId(bizId).setCouponId(couponId).setType(1))
                .collect(Collectors.toList());
        // 2.2 保存
        scopeService.saveBatch(list);
    }

    @Override
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {
        // 1. 分页查询
        Integer status = query.getStatus();
        String name = query.getName();
        Integer type = query.getType();
        Page<Coupon> page = lambdaQuery()
                .eq(type != null, Coupon::getDiscountType, type)
                .eq(status != null, Coupon::getStatus, status)
                .like(StringUtils.isNotBlank(name), Coupon::getName, name)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        // 2. 处理VO
        List<Coupon> records = page.getRecords();
        if(CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        List<CouponPageVO> list = BeanUtils.copyList(records, CouponPageVO.class);
        // 3. 返回
        return PageDTO.of(page, list);
    }

    @Override
    public void beginIssue(CouponIssueFormDTO dto) {
        // 1. 查询优惠卷
        Coupon coupon = getById(dto.getId());
        if(coupon == null) {
            throw new BadRequestException("优惠卷不存在! ");
        }
        // 2. 判断优惠卷状态, 是否是暂停或待发放
        if(coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != CouponStatus.PAUSE) {
            throw new BadRequestException("优惠卷状态错误! ");
        }
        // 3. 判断是否时立刻发放
        LocalDateTime issueBeginTime = dto.getIssueBeginTime();
        LocalDateTime now = LocalDateTime.now();
        // 开始发放时间为null或者发放时间小于等于当前时间, 都代表立刻发放
        boolean isBegin = issueBeginTime == null || !issueBeginTime.isAfter(now);
        // 4. 更新优惠卷
        // 4.1 拷贝属性到PO
        Coupon c = BeanUtils.copyBean(dto, Coupon.class);
        // 4.2 更新状态
        if(isBegin) {
            c.setStatus(CouponStatus.ISSUING);
            c.setIssueBeginTime(now);
        } else {
            c.setStatus(CouponStatus.UN_ISSUE);
        }
        // 4.3 写入数据库
        updateById(c);

        // 5. 判断是否需要生成兑换码, 优惠卷类型必须是兑换码, 优惠卷状态必须是待发放
        if(coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == CouponStatus.DRAFT) {
            coupon.setIssueEndTime(c.getIssueEndTime());
            codeService.asyncGenerateCode(coupon);
        }
    }

    @Override
    public CouponDetailVO queryCouponById(Long id) {
        // 1. 查询优惠卷基础数据
        // 表: coupon 查询条件: id
        Coupon coupon = this.getById(id);

        // 2. 转换VO
        CouponDetailVO vo = BeanUtils.copyBean(coupon, CouponDetailVO.class);
        if(vo == null || !coupon.getSpecific()) {
            return vo; // vo不存在或者没有在限定范围内, 直接返回
        }

        // 3. 查询优惠卷的限定范围
        // 表: coupon_scope 条件: coupon_id
        List<CouponScope> scopes = scopeService.lambdaQuery().
                eq(CouponScope::getCouponId, id)
                .list();
        if(CollUtils.isEmpty(scopes)) {
            return vo;
        }

        // 4. 转换VO
        List<CouponScopeVO> scopeVOS = scopes.stream()
                .map(CouponScope::getBizId)
                .map(cateId -> new CouponScopeVO(cateId, categoryCache.getNameByLv3Id(cateId)))
                .collect(Collectors.toList());

        // 5. 补全CouponDetailVO的限定范围数据
        vo.setScopes(scopeVOS);
        return vo;
    }

    @Override
    @Transactional
    public void updateCouponById(Long id, CouponFormDTO dto) {
        // 1. 检验优惠卷id
        if(id == null || !id.equals(dto.getId())) {
            throw new BadRequestException("非法参数或优惠卷id不一致");
        }

        // 2. 根据id查询优惠卷
        Coupon coupon = this.getById(id);
        if(coupon == null) {
            throw new BadRequestException("优惠卷不存在");
        }

        // 3. 校验优惠卷状态, 只有待发放才可以修改
        if(coupon.getStatus() != CouponStatus.DRAFT) {
            throw new BizIllegalException("只有待发放的优惠卷才可以修改");
        }

        // 4. 转换po
        Coupon updateCoupon = BeanUtils.copyBean(dto, Coupon.class);

        // 5. 更新优惠卷
        this.updateById(updateCoupon);

        // 6. 更新优惠卷的限定范围
        // 6.1 如果之前有限定范围, 先删除旧的
        if(coupon.getSpecific()) {
            scopeService.remove(new QueryWrapper<CouponScope>().eq("coupon_id", id));
        }
        // 6.2 判断是否需要更新限定范围
        if(dto.getSpecific()) {
            // 6.3 检验用于更新的是否为空
            List<Long> scopes = dto.getScopes();
            if(CollUtils.isEmpty(scopes)) {
                throw new BadRequestException("更新限定的范围, 分类id集合不能为空");
            }
            // 6.4 保存新的优惠卷的限定范围
            List<CouponScope> couponScopeList = scopes
                    .stream()
                    .map(aLong -> new CouponScope().setCouponId(coupon.getId()).setBizId(aLong).setType(1))
                    .collect(Collectors.toList());
            scopeService.saveBatch(couponScopeList);
        }
    }

    @Override
    @Transactional
    public void deleteCouponById(Long id) {
        // 1. 根据id查询优惠卷
        Coupon coupon = this.getById(id);
        if(coupon == null) {
            throw new BadRequestException("优惠卷不存在");
        }

        // 2. 校验优惠卷状态, 只有待发放才可以删除
        if(coupon.getStatus() != CouponStatus.DRAFT) {
            throw new BizIllegalException("只有待发放的优惠卷才可以删除");
        }

        // 3. 删除优惠卷
        this.removeById(id);

        // 4. 删除优惠卷的限定范围
        if(!coupon.getSpecific()) {
            return;
        }
        scopeService.remove(new QueryWrapper<CouponScope>().eq("coupon_id", id));
    }

    @Override
    public void issueCouponBatch(List<Coupon> list) {
        // 1. 更新列表中所有的优惠卷状态为发放中
        for (Coupon coupon : list) {
            coupon.setStatus(CouponStatus.ISSUING);
        }

        // 2. 批量更新优惠卷状态
        this.updateBatchById(list);
    }

    @Override
    public void endCouponBatch(List<Coupon> list) {
        // 1. 更新列表中所有的优惠卷状态为已结束
        for (Coupon coupon : list) {
            coupon.setStatus(CouponStatus.FINISHED);
        }

        // 2. 批量更新优惠卷状态
        this.updateBatchById(list);
    }

    @Override
    public void pauseIssueCouponById(Long id) {
        // 1. 根据id查询优惠卷
        Coupon coupon = this.getById(id);
        if(coupon == null) {
            throw new BadRequestException("优惠卷不存在");
        }

        // 2. 检验优惠卷状态, 只有未开始和发放中的才可以暂停
        if(coupon.getStatus() != CouponStatus.UN_ISSUE && coupon.getStatus() != CouponStatus.ISSUING) {
            throw new BizIllegalException("只有未开始和发放中的优惠卷才可以暂停");
        }

        // 3. 更新优惠卷状态为暂停
        this.lambdaUpdate()
                .eq(Coupon::getId, id)
                .set(Coupon::getStatus, CouponStatus.PAUSE)
                .in(Coupon::getStatus, CouponStatus.UN_ISSUE, CouponStatus.ISSUING)
                .update();
    }

    @Override
    public List<CouponVO> queryIssuingCoupons() {
        // 1. 查询发放中的优惠卷
        List<Coupon> coupons = lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if(CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }

        // 2. 统计当前用户已经领取的优惠卷的信息
        List<Long> couponIds = coupons.stream().map(Coupon::getId).collect(Collectors.toList());
        // 2.1 查询当前用户已经领取的优惠卷的数据
        List<UserCoupon> userCoupons = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, couponIds)
                .list();
        // 2.2 统计当前用户对优惠卷的已经领取数量
        Map<Long, Long> issuedMap = userCoupons.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 2.3 统计当前用户对优惠卷的已经领取并且未使用的数量
        Map<Long, Long> unusedMap = userCoupons.stream()
                .filter(uc -> uc.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));

        // 3. 转换VO返回
        List<CouponVO> list = new ArrayList<>(coupons.size());
        for (Coupon c : coupons) {
            // 3.1 拷贝po属性到vo
            CouponVO vo = BeanUtils.copyBean(c, CouponVO.class);
            list.add(vo);
            // 3.2 是否可以领取: 已经领取的数量 < 优惠卷总数量 && 当前用户已经领取的数量 < 每人限领数量
            vo.setAvailable(
                    c.getIssueNum() < c.getTotalNum()
                    && issuedMap.getOrDefault(c.getId(), 0L) < c.getUserLimit()
            );
            // 3.3 是否可以使用: 当前用户已经领取并且未使用的优惠卷数量 > 0
            vo.setReceived(unusedMap.getOrDefault(c.getId(), 0L) > 0);
        }
        return list;
    }
}
