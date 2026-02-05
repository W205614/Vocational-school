package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-04
 */
@Api(tags = "回答或评论相关接口")
@RestController
@RequestMapping("/admin/replies")
@RequiredArgsConstructor
public class InteractionReplyAdminController {

    private final IInteractionReplyService replyService;

    @ApiOperation("分页查询回答或评论列表")
    @GetMapping("/page")
    public PageDTO<ReplyVO> queryReplyVOPageAdmin(ReplyPageQuery query, Boolean isAdmin) {
        return replyService.queryReplyVOPage(query, true);
    }

    @ApiOperation("隐藏或显示回答或评论")
    @PutMapping("/{id}/hidden/{hidden}")
    public void hiddenReplyAdmin(@PathVariable("id") Long id, @PathVariable("hidden") Boolean hidden) {
        replyService.hiddenReplyAdmin(id, hidden);
    }

    @ApiOperation("根据id查询回答或评论详情")
    @GetMapping("/{id}")
    public ReplyVO queryReplyVOByIdAdmin(@PathVariable("id") Long id) {
        return replyService.queryReplyVOByIdAdmin(id);
    }
}
