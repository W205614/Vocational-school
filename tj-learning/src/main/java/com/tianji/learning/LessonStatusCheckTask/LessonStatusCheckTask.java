package com.tianji.learning.LessonStatusCheckTask;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class LessonStatusCheckTask {

    private final ILearningLessonService lessonService;

    /**
     * 定时检查课表中的课程状态
     */
    @Scheduled(cron = "0 * * * * ?") // 每分钟执行一次
    public void checkLessonStatus() {
        //1.日志输出，方便观察
        LocalDateTime now = LocalDateTime.now();
        log.info("定时检查课表中的课程是否过期: {}", now);

        //2.查询课表中所有状态为未过期的课程(不需要区分用户)
        List<LearningLesson> notExpiredCourses = lessonService.list(Wrappers.<LearningLesson>lambdaQuery()
                .ne(LearningLesson::getStatus, LessonStatus.EXPIRED));  //ne表示不等于(not equal)

        //3.遍历所有未过期的课程，判断是否过期(当前时间在过期时间之后)
        for (LearningLesson notExpiredCourse : notExpiredCourses) {
            if (now.isAfter(notExpiredCourse.getExpireTime())) {
                notExpiredCourse.setStatus(LessonStatus.EXPIRED);
            }
        }
        //4.批量更新课程状态
        lessonService.updateBatchById(notExpiredCourses);
    }
}
