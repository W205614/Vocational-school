package com.zhixuetang.api.client.learning.fallback;

import com.zhixuetang.api.client.learning.LearningClient;
import com.zhixuetang.api.dto.leanring.CourseStudyCounts;
import com.zhixuetang.api.dto.leanring.LearningLessonDTO;
import com.zhixuetang.common.exceptions.CommonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.Map;
import java.util.Set;

@Slf4j
public class LearningClientFallback implements FallbackFactory<LearningClient> {

    @Override
    public LearningClient create(Throwable cause) {
        log.error("查询学习服务异常", cause);
        return new LearningClient() {
            @Override
            public Map<Long, CourseStudyCounts> courseStudyCounts(Set<Long> courseIds) {
                throw new CommonException("学习人数暂时无法查询", cause);
            }

            @Override
            public Integer countLearningLessonByCourse(Long courseId) {
                return 0;
            }

            @Override
            public Long isLessonValid(Long courseId) {
                return null;
            }

            @Override
            public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
                return null;
            }
        };
    }
}
