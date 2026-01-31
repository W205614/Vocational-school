package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-01-31
 */
@Api(tags = "我的课表相关接口")
@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LearningLessonController {

    private final ILearningLessonService lessonService;

    @ApiOperation("查询我的课表")
    @GetMapping("/page")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        return lessonService.queryMyLessons(query);
    }

    @GetMapping("/now")
    @ApiOperation("查询我正在学习的课程")
    public LearningLessonVO queryMyCurrentLesson(){
        return lessonService.queryMyCurrentLesson();
    }

    @GetMapping("/{courseId}/valid")
    @ApiOperation("校验当前用户是否可以学习当前课程")
    Long isLessonValid(@PathVariable("courseId") Long courseId){
        return lessonService.isLessonValid(courseId);
    }

    @GetMapping("/{courseId}")
    @ApiOperation("查询用户课表中指定课程状态")
    public LearningLessonVO isLessonStatus(@PathVariable Long courseId){
        return lessonService.isLessonStatus(courseId);
    }

    @GetMapping("/lessons/{courseId}/count")
    @ApiOperation("统计课程学习人数")
    public Integer countLearningLessonByCourse(@PathVariable("courseId") Long courseId){
        return lessonService.countLearningLessonByCourse(courseId);
    }

    @DeleteMapping("/lessons/{courseId}")
    @ApiOperation("删除表中的某课程")
    private void deleteLesson(@PathVariable Long courseId){
        lessonService.deleteLesson(courseId);
    }
}
