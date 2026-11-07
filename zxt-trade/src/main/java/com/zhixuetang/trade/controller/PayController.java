package com.zhixuetang.trade.controller;

import com.zhixuetang.trade.domain.dto.PayApplyFormDTO;
import com.zhixuetang.trade.domain.vo.PayChannelVO;
import com.zhixuetang.trade.service.IPayService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "支付相关接口")
@RestController
@RequestMapping("pay")
@RequiredArgsConstructor
public class PayController {

    private final IPayService payService;

    @PostMapping("/order")
    @Operation(summary = "支付申请,返回支付二维码url")
    public String applyPayOrder(@RequestBody PayApplyFormDTO payApply) {
        return payService.applyPayOrder(payApply);
    }

    @GetMapping("/channels")
    @Operation(summary = "获取支付渠道列表接口")
    public List<PayChannelVO> queryPayChannels() {
        return payService.queryPayChannels();
    }
}
