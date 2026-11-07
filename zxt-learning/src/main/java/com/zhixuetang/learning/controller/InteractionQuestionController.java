package com.zhixuetang.learning.controller;

import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.learning.domain.dto.QuestionFormDTO;
import com.zhixuetang.learning.domain.query.QuestionPageQuery;
import com.zhixuetang.learning.domain.vo.QuestionVO;
import com.zhixuetang.learning.service.IInteractionQuestionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-04
 */
@RestController
@RequestMapping("/questions")
@Tag(name = "互动问答的相关接口")
@RequiredArgsConstructor
public class InteractionQuestionController {

    private final IInteractionQuestionService questionService;
    private final com.zhixuetang.common.autoconfigure.reliability.OperationStore operations;
    private final com.zhixuetang.api.client.course.CourseClient courses;

    @Operation(summary = "新增互动问题")
    @PostMapping
    public org.springframework.http.ResponseEntity<?> saveQuestion(
            @Valid @RequestBody QuestionFormDTO questionDTO,
            @RequestHeader("Idempotency-Key") String key) {
        var section = courses.sectionInfo(questionDTO.getSectionId());
        if (section == null
                || !java.util.Objects.equals(section.getCourseId(), questionDTO.getCourseId()))
            throw new com.zhixuetang.common.exceptions.BadRequestException("小节不属于该课程");
        return org.springframework.http.ResponseEntity.accepted()
                .body(
                        operations.submit(
                                com.zhixuetang.common.utils.UserContext.requireUser(),
                                "DISCUSSION_CREATE",
                                key,
                                new com.zhixuetang.learning.service.impl.DiscussionOperationHandler
                                        .Request(
                                        "QUESTION",
                                        com.zhixuetang.common.utils.UserContext.getRole(),
                                        questionDTO,
                                        null)));
    }

    @Operation(summary = "分页查询互动问题")
    @GetMapping("/page")
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        return questionService.queryQuestionPage(query);
    }

    @Operation(summary = "根据id查询互动问题")
    @GetMapping("/{id}")
    public QuestionVO queryQuestionById(@PathVariable("id") Long id) {
        return questionService.queryQuestionById(id);
    }

    @Operation(summary = "修改互动问题")
    @PutMapping("/{id}")
    public void updateQuestion(@PathVariable Long id, @RequestBody QuestionFormDTO dto) {
        questionService.updateQuestion(id, dto);
    }

    @Operation(summary = "删除我的问题")
    @DeleteMapping("/{id}")
    public void deleteQuestionById(@PathVariable Long id) {
        questionService.deleteQuestionById(id);
    }
}
