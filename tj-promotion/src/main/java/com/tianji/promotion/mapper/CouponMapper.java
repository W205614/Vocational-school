package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-11
 */
public interface CouponMapper extends BaseMapper<Coupon> {

    @Update("update coupon set issue_num = issue_num + 1 where id = #{couponId} and issue_num < total_num")
    int incrIssueNum(@Param("couponId") Long couponId);

    @Update({"<script>", "update coupon set used_num = used_num + #{i} where id in",
            "<foreach collection='couponIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "and used_num + #{i} between 0 and issue_num", "</script>"})
    int incrUsedNum(@Param("couponIds") List<Long> couponIds, @Param("i") Integer i);
}
