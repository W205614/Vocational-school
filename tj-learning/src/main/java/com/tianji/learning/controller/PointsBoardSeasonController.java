package com.tianji.learning.controller;


import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-07
 */
@Tag(name = "赛季相关接口")
@RestController
@RequestMapping("/board/seasons")
@RequiredArgsConstructor
public class PointsBoardSeasonController {

    private final IPointsBoardSeasonService pointsBoardSeasonService;

    @Operation(summary = "查询赛季列表")
    @GetMapping("/list")
    public List<PointsBoardSeason> queryPointsBoardSeasonList() {
        return pointsBoardSeasonService.list();
    }
}
