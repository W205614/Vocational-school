package com.zhixuetang.promotion.service;

import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.promotion.domain.dto.CouponFormDTO;
import com.zhixuetang.promotion.domain.dto.CouponIssueFormDTO;
import com.zhixuetang.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.spring.service.IService;
import com.zhixuetang.promotion.domain.query.CouponQuery;
import com.zhixuetang.promotion.domain.vo.CouponDetailVO;
import com.zhixuetang.promotion.domain.vo.CouponPageVO;
import com.zhixuetang.promotion.domain.vo.CouponVO;

import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-11
 */
public interface ICouponService extends IService<Coupon> {

    void saveCoupon(CouponFormDTO dto);

    PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query);

    void beginIssue(CouponIssueFormDTO dto);

    CouponDetailVO queryCouponById(Long id);

    void updateCouponById(Long id, CouponFormDTO dto);

    void deleteCouponById(Long id);

    void issueCouponBatch(List<Coupon> list);

    void endCouponBatch(List<Coupon> list);

    void pauseIssueCouponById(Long id);

    List<CouponVO> queryIssuingCoupons();
}
