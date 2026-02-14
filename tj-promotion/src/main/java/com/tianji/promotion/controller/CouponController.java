package com.tianji.promotion.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-11
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/coupons")
@Api(tags = "优惠卷相关接口")
public class CouponController {

    private final ICouponService couponService;

    @ApiOperation("新增优惠卷接口")
    @PostMapping
    public void saveCoupon(@RequestBody @Valid CouponFormDTO dto) {
        couponService.saveCoupon(dto);
    }

    @ApiOperation("分页查询优惠卷接口")
    @GetMapping("/page")
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {
        return couponService.queryCouponByPage(query);
    }

    @ApiOperation("发放优惠卷接口")
    @PutMapping("/{id}/issue")
    public void beginIssue(@RequestBody @Valid CouponIssueFormDTO dto) {
        couponService.beginIssue(dto);
    }

    @ApiOperation("根据id查询优惠卷")
    @GetMapping("/{id}")
    public CouponDetailVO queryCouponById(@PathVariable Long id) {
        return couponService.queryCouponById(id);
    }

    @ApiOperation("根据id修改优惠卷")
    @PutMapping("/{id}")
    public void updateCouponById(@PathVariable Long id, @RequestBody @Validated CouponFormDTO dto) {
        couponService.updateCouponById(id, dto);
    }

    @ApiOperation("根据id删除优惠卷")
    @DeleteMapping("/{id}")
    public void deleteCouponById(@PathVariable Long id) {
        couponService.deleteCouponById(id);
    }

    @ApiOperation("根据id暂停发放优惠卷")
    @PutMapping("/{id}/pause")
    public void pauseIssueCouponById(@PathVariable Long id) {
        couponService.pauseIssueCouponById(id);
    }

    @ApiOperation("查询发放中的优惠卷列表")
    @GetMapping("/list")
    public List<CouponVO> queryIssuingCoupons() {
        return couponService.queryIssuingCoupons();
    }
}
