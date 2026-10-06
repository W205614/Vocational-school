package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-01-31
 */
@Tag(name = "我的课表相关接口")
@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LearningLessonController {

    private final ILearningLessonService lessonService;

    @Operation(summary = "查询我的课表")
    @GetMapping("/page")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        return lessonService.queryMyLessons(query);
    }

    @GetMapping("/now")
    @Operation(summary = "查询我正在学习的课程")
    public LearningLessonVO queryMyCurrentLesson(){
        return lessonService.queryMyCurrentLesson();
    }

    @GetMapping("/{courseId}/valid")
    @Operation(summary = "校验当前用户是否可以学习当前课程")
    Long isLessonValid(@PathVariable("courseId") Long courseId){
        return lessonService.isLessonValid(courseId);
    }

    @GetMapping("/{courseId}")
    @Operation(summary = "查询用户课表中指定课程状态")
    public LearningLessonVO isLessonStatus(@PathVariable Long courseId){
        return lessonService.isLessonStatus(courseId);
    }

    @GetMapping("/lessons/{courseId}/count")
    @Operation(summary = "统计课程学习人数")
    public Integer countLearningLessonByCourse(@PathVariable("courseId") Long courseId){
        return lessonService.countLearningLessonByCourse(courseId);
    }

    @DeleteMapping("/lessons/{courseId}")
    @Operation(summary = "删除表中的某课程")
    private void deleteLesson(@PathVariable Long courseId){
        lessonService.deleteLesson(courseId);
    }

    @Operation(summary = "创建学习计划")
    @PostMapping("/plans")
    public void createLearningPlans(@Valid @RequestBody LearningPlanDTO planDTO) {
        lessonService.createLearningPlans(planDTO.getCourseId(), planDTO.getFreq());
    }

    @Operation(summary = "查询我的学习计划")
    @GetMapping("/plans")
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        return lessonService.queryMyPlans(query);
    }
}
