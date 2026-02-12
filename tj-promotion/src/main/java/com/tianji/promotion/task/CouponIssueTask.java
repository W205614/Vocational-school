package com.tianji.promotion.task;

import com.tianji.common.utils.CollUtils;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.service.ICouponService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueTask {

    private final ICouponService couponService;

    /**
     * 定时开始发放优惠卷
     */
    @XxlJob("beginCouponIssueJob")
    public void beginCouponIssue() {
        log.debug("正在执行开始发放优惠卷的任务");
        // 1. 查询所有符合条件的优惠卷
        // 表: coupon, 条件: 状态为未发放, 开始发放时间小于当前时间
        List<Coupon> list = couponService
                .lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.UN_ISSUE)
                .le(Coupon::getIssueBeginTime, LocalDateTime.now())
                .list();

        // 2. 判断需要发放的优惠卷集合是否为空
        if(CollUtils.isEmpty(list)) {
            log.debug("没有需要发放的优惠卷");
            return;
        }

        // 3. 批量发放符合条件的优惠卷
        log.debug("开始批量发放{}张优惠卷", list.size());
        couponService.issueCouponBatch(list);
    }

    /**
     * 定时结束发放优惠卷
     */
    @XxlJob("endCouponIssueJob")
    public void endCouponIssue() {
        log.debug("正在执行结束发放优惠卷的任务");
        // 1. 查询所有符合条件的优惠卷
        // 表: coupon 条件: 状态为进行中, 结束时间小于当前时间
        List<Coupon> list = couponService
                .lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .le(Coupon::getIssueEndTime, LocalDateTime.now())
                .list();

        // 2. 判断是否为空
        if(CollUtils.isEmpty(list)) {
            log.debug("没有需要结束发放的优惠卷");
            return;
        }

        // 3. 批量发放符合条件的优惠卷
        log.debug("开始批量结束发放{}张优惠卷", list.size());
        couponService.endCouponBatch(list);
    }
}
