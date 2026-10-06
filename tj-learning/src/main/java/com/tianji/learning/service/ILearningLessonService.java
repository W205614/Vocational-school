package com.tianji.learning.service;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.spring.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;

import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-01-31
 */
public interface ILearningLessonService extends IService<LearningLesson> {

    void addUserLessons(Long userId, List<Long> courseIds);

    PageDTO<LearningLessonVO> queryMyLessons(PageQuery query);

    LearningLessonVO queryMyCurrentLesson();

    void removeUserLessons(OrderBasicDTO order);

    Long isLessonValid(Long courseId);

    LearningLessonVO isLessonStatus(Long courseId);

    Integer countLearningLessonByCourse(Long courseId);

    void deleteLesson(Long courseId);

    LearningLesson queryByUserIdAndCourseId(Long userId, Long courseId);

    void createLearningPlans(Long courseId, Integer freq);

    LearningPlanPageVO queryMyPlans(PageQuery query);
}
