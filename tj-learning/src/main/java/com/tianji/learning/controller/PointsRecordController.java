package com.tianji.learning.controller;


import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.service.IPointsRecordService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-07
 */
@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
@Tag(name = "积分相关接口")
public class PointsRecordController {

    private final IPointsRecordService recordService;

    @GetMapping("/today")
    @Operation(summary = "查询我的今日积分情况")
    public List<PointsStatisticsVO> queryMyPointsToday() {
        return recordService.queryMyPointsToday();
    }
}
