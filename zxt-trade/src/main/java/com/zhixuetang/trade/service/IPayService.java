package com.zhixuetang.trade.service;

import com.zhixuetang.trade.domain.dto.OrderDelayQueryDTO;
import com.zhixuetang.trade.domain.dto.PayApplyFormDTO;
import com.zhixuetang.trade.domain.vo.PayChannelVO;

import java.util.List;

public interface IPayService {
    List<PayChannelVO> queryPayChannels();

    String applyPayOrder(PayApplyFormDTO payApply);

    void queryPayResult(OrderDelayQueryDTO message);
}
