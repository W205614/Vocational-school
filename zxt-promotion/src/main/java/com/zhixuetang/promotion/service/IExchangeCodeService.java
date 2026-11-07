package com.zhixuetang.promotion.service;

import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.promotion.domain.po.Coupon;
import com.zhixuetang.promotion.domain.po.ExchangeCode;
import com.baomidou.mybatisplus.spring.service.IService;
import com.zhixuetang.promotion.domain.query.CodeQuery;
import com.zhixuetang.promotion.domain.vo.ExchangeCodeVO;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-11
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {

    void asyncGenerateCode(Coupon coupon);

    PageDTO<ExchangeCodeVO> queryExchangeCodeByPage(CodeQuery query);

    boolean updateExchangeMark(long serialNum, boolean mark);

    Long exchangeTargetId(long serialNum);
}
