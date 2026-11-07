package com.zhixuetang.learning.service;

import com.zhixuetang.api.dto.leanring.LearningLessonDTO;
import com.zhixuetang.learning.domain.dto.LearningRecordFormDTO;
import com.zhixuetang.learning.domain.po.LearningRecord;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 * 学习记录表 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-02-02
 */
public interface ILearningRecordService extends IService<LearningRecord> {

    LearningLessonDTO queryLearningRecordByCourse(Long courseId);

    void addLearningRecord(LearningRecordFormDTO formDTO);
}
