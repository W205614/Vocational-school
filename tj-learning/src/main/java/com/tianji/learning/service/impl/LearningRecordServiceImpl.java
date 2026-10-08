package com.tianji.learning.service.impl;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.leanring.*;
import com.tianji.common.exceptions.*;
import com.tianji.common.utils.*;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.*;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.*;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper,LearningRecord> implements ILearningRecordService {
    private final ILearningLessonService lessonService;
    private final CourseClient courseClient;
    private final LearningRecordDelayTaskHandler taskHandler;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager manager;
    private final LearningEntitlementService entitlements;
    @Override public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        LearningLesson lesson=lessonService.queryByUserIdAndCourseId(UserContext.requireUser(),courseId);
        if(lesson==null) return null;
        LearningLessonDTO dto=new LearningLessonDTO();dto.setId(lesson.getId());dto.setLatestSectionId(lesson.getLatestSectionId());
        dto.setRecords(BeanUtils.copyList(lambdaQuery().eq(LearningRecord::getLessonId,lesson.getId()).list(),LearningRecordDTO.class));
        return dto;
    }
    @Override public void addLearningRecord(LearningRecordFormDTO form) {
        long user=UserContext.requireUser();
        if(form.getSectionType()!=SectionType.VIDEO) throw new BadRequestException("考试完成由评分结果更新");
        LearningLesson lesson=lessonService.getById(form.getLessonId());
        if(lesson==null || !Objects.equals(lesson.getUserId(),user)) throw new BadRequestException("课表不存在");
        // Remote metadata is fetched before taking a local database lock.
        var section=courseClient.sectionInfo(form.getSectionId());
        var course=courseClient.getCourseInfoById(lesson.getCourseId(),false,false);
        if(section==null || !Integer.valueOf(2).equals(section.getType()) || !Objects.equals(section.getCourseId(),lesson.getCourseId()) || course==null) throw new BadRequestException("视频小节不属于该课程");
        Integer duration=section.getMediaDuration(),moment=form.getMoment();
        if(duration==null || duration<=0 || moment==null || moment<0 || moment>duration) throw new BadRequestException("播放进度或视频时长无效");
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            if(entitlements.require(user,lesson.getCourseId())!=lesson.getId()) throw new BadRequestException("课表权益来源不一致");
            var locked=jdbc.queryForList("SELECT * FROM learning_lesson WHERE id=? AND user_id=? FOR UPDATE",lesson.getId(),user);
            if(locked.isEmpty()) throw new BadRequestException("课程权益已失效");
            var rows=jdbc.queryForList("SELECT id,finished FROM learning_record WHERE lesson_id=? AND section_id=?",lesson.getId(),form.getSectionId());
            long recordId;
            if(rows.isEmpty()) {
                recordId=IdWorker.getId();
                jdbc.update("INSERT INTO learning_record(id,lesson_id,section_id,user_id,moment,finished) VALUES(?,?,?,?,0,0)",recordId,lesson.getId(),form.getSectionId(),user);
            } else recordId=((Number)rows.getFirst().get("id")).longValue();
            LearningRecord record=new LearningRecord().setId(recordId).setLessonId(lesson.getId()).setSectionId(form.getSectionId()).setMoment(moment);
            taskHandler.addLearningRecordTask(record);
            if((long)moment*2>=duration && jdbc.update("UPDATE learning_record SET moment=GREATEST(COALESCE(moment,0),?),finished=1,finish_time=CURRENT_TIMESTAMP(3) WHERE id=? AND finished=0",moment,recordId)==1) {
                jdbc.update("UPDATE learning_lesson SET status=CASE WHEN learned_sections+1>=? THEN 2 ELSE 1 END,learned_sections=learned_sections+1,latest_section_id=?,latest_learn_time=CURRENT_TIMESTAMP(3) WHERE id=?",course.getSectionNum(),form.getSectionId(),lesson.getId());
            }
        });
    }
}
