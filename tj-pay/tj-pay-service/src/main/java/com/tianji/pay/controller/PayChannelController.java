package com.tianji.pay.controller;


import com.tianji.common.utils.BeanUtils;
import com.tianji.pay.sdk.dto.PayChannelDTO;
import com.tianji.pay.service.IPayChannelService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * <p>
 * 支付渠道 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-26
 */
@Tag(name = "支付相关接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/pay-channels")
public class PayChannelController {

    private final IPayChannelService channelService;
    @jakarta.annotation.Resource private java.util.Map<String,com.tianji.pay.third.IPayService> payServiceChannels;

    @Operation(summary = "查询支付渠道列表")
    @GetMapping("/list")
    public List<PayChannelDTO> listAllPayChannels(){
        com.tianji.common.utils.InternalAuth.requireService();
        if(payServiceChannels.isEmpty())return java.util.List.of();
        return BeanUtils.copyList(channelService.lambdaQuery().in(com.tianji.pay.domain.po.PayChannel::getChannelCode,payServiceChannels.keySet()).list(), PayChannelDTO.class);
    }

    @Operation(summary = "添加支付渠道")
    @PostMapping
    public Long addPayChannel(@Valid @RequestBody PayChannelDTO channelDTO){
        com.tianji.common.utils.UserContext.requireAdmin();
        return channelService.addPayChannel(channelDTO);
    }

    @Operation(summary = "修改支付渠道")
    @PutMapping("/{id}")
    public void updatePayChannel(
            @Parameter(description = "支付渠道id") @PathVariable("id") Long id,
            @RequestBody PayChannelDTO channelDTO){
        com.tianji.common.utils.UserContext.requireAdmin();
        channelDTO.setId(id);
        channelService.updatePayChannel(channelDTO);
    }
}
