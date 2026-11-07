package com.zhixuetang.promotion.controller;

import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.promotion.domain.query.CodeQuery;
import com.zhixuetang.promotion.domain.vo.ExchangeCodeVO;
import com.zhixuetang.promotion.service.IExchangeCodeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 兑换码 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-11
 */
@Tag(name = "兑换码相关接口")
@RestController
@RequestMapping("/codes")
@RequiredArgsConstructor
public class ExchangeCodeController {
    private final IExchangeCodeService exchangeCodeService;

    @Operation(summary = "分页查询兑换码")
    @GetMapping("/page")
    public PageDTO<ExchangeCodeVO> queryExchangeCodeByPage(@Validated CodeQuery query) {
        return exchangeCodeService.queryExchangeCodeByPage(query);
    }
}
