package com.zhixuetang.learning.controller;

import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.learning.domain.dto.ReplyDTO;
import com.zhixuetang.learning.domain.query.ReplyPageQuery;
import com.zhixuetang.learning.domain.vo.ReplyVO;
import com.zhixuetang.learning.service.IInteractionReplyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-04
 */
@Tag(name = "回答或评论相关接口")
@RestController
@RequestMapping("/replies")
@RequiredArgsConstructor
public class InteractionReplyController {

    private final IInteractionReplyService replyService;
    private final com.zhixuetang.common.autoconfigure.reliability.OperationStore operations;

    @Operation(summary = "新增回答或评论")
    @PostMapping
    public org.springframework.http.ResponseEntity<?> saveReply(
            @RequestBody @Validated ReplyDTO dto, @RequestHeader("Idempotency-Key") String key) {
        return org.springframework.http.ResponseEntity.accepted()
                .body(
                        operations.submit(
                                com.zhixuetang.common.utils.UserContext.requireUser(),
                                "DISCUSSION_CREATE",
                                key,
                                new com.zhixuetang.learning.service.impl.DiscussionOperationHandler
                                        .Request(
                                        "REPLY",
                                        com.zhixuetang.common.utils.UserContext.getRole(),
                                        null,
                                        dto)));
    }

    @Operation(summary = "分页查询回答或评论列表")
    @GetMapping("/page")
    public PageDTO<ReplyVO> queryReplyVOPage(ReplyPageQuery query, Boolean isAdmin) {
        return replyService.queryReplyVOPage(query, false);
    }
}
