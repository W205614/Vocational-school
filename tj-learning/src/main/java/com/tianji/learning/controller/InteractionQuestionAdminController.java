package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-04
 */
@RestController
@RequestMapping("/admin/questions")
@Api(tags = "互动问答的相关接口")
@RequiredArgsConstructor
public class InteractionQuestionAdminController {

    private final IInteractionQuestionService questionService;

    @ApiOperation("管理端分页查询互动问题")
    @GetMapping("/page")
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        return questionService.queryQuestionPageAdmin(query);
    }

    @ApiOperation("隐藏或显示问题")
    @PutMapping("/{id}/hidden/{hidden}")
    public void hiddenQuestionAdmin(@PathVariable("id") Long id, @PathVariable("hidden") Boolean hidden) {
        questionService.hiddenQuestionAdmin(id, hidden);
    }

    @ApiOperation("根据id查询问题详情")
    @GetMapping("/{id}")
    public QuestionAdminVO queryQuestionAdminVOById(@PathVariable("id") Long id) {
        return questionService.queryQuestionAdminVOById(id);
    }
}
