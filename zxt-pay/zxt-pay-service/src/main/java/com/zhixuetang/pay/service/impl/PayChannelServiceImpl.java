package com.zhixuetang.pay.service.impl;

import com.zhixuetang.common.utils.BeanUtils;
import com.zhixuetang.pay.sdk.dto.PayChannelDTO;
import com.zhixuetang.pay.domain.po.PayChannel;
import com.zhixuetang.pay.mapper.PayChannelMapper;
import com.zhixuetang.pay.service.IPayChannelService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 支付渠道 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-26
 */
@Service
public class PayChannelServiceImpl extends ServiceImpl<PayChannelMapper, PayChannel>
        implements IPayChannelService {

    @Override
    public Long addPayChannel(PayChannelDTO channelDTO) {
        // 1.属性转换
        PayChannel payChannel = BeanUtils.toBean(channelDTO, PayChannel.class);
        // 2.保存
        save(payChannel);
        return payChannel.getId();
    }

    @Override
    public void updatePayChannel(PayChannelDTO channelDTO) {
        // 1.属性转换
        PayChannel payChannel = BeanUtils.toBean(channelDTO, PayChannel.class);
        // 2.保存
        updateById(payChannel);
    }
}
