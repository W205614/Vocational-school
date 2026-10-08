package com.tianji.trade.mapper;

import com.tianji.trade.domain.po.Order;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 订单 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-29
 */
public interface OrderMapper extends BaseMapper<Order> {

    Order getById(Long id);
    /** Financial callbacks retain visibility of soft-deleted facts. This is not a user-facing query. */
    @org.apache.ibatis.annotations.Select("SELECT id,user_id,status,pay_order_no,deleted FROM `order` WHERE id=#{id} FOR UPDATE")
    Order selectRetainedForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);
}
