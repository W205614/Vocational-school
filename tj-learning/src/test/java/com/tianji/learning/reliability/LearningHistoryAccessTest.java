package com.tianji.learning.reliability;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.*;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.impl.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LearningHistoryAccessTest {
    private LearningRecordServiceImpl service;
    private LearningEntitlementService rights;
    private LearningLesson lesson;
    private LearningRecord history;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        UserContext.setUser(800L);
        lesson=new LearningLesson().setId(20L).setUserId(800L).setCourseId(10L)
                .setStatus(LessonStatus.FINISHED).setLearnedSections(4)
                .setExpireTime(LocalDateTime.now().plusYears(1));
        history=new LearningRecord().setId(21L).setLessonId(20L).setSectionId(30L).setMoment(42);
        var lessons=mock(ILearningLessonService.class);
        when(lessons.queryByUserIdAndCourseId(800L,10L)).thenReturn(lesson);
        rights=mock(LearningEntitlementService.class);
        service=spy(new LearningRecordServiceImpl(lessons,null,null,null,null,rights));
        LambdaQueryChainWrapper<LearningRecord> query=mock(LambdaQueryChainWrapper.class,RETURNS_SELF);
        doReturn(query).when(service).lambdaQuery();
        doReturn(query).when(query).eq(any(),any());
        when(query.list()).thenReturn(List.of(history));
    }
    @AfterEach void cleanup(){UserContext.removeUser();}

    @Test void revokedEnrollmentKeepsHistoryWithoutOfferingActiveLearning() {
        when(rights.summaries(800L,List.of(10L))).thenReturn(Map.of());
        var view=service.queryLearningRecordByCourse(10L);
        assertEquals(3,view.getStatus());assertNull(view.getExpireTime());
        assertEquals(4,view.getLearnedSections());assertEquals(42,view.getRecords().getFirst().getMoment());
    }
    @Test void currentFiniteRightsOverrideStaleProjectedExpiry() {
        var expiry=LocalDateTime.now().plusMonths(1);
        when(rights.summaries(800L,List.of(10L))).thenReturn(Map.of(10L,new LearningEntitlementService.Summary(expiry,2)));
        var view=service.queryLearningRecordByCourse(10L);
        assertEquals(2,view.getStatus());assertEquals(expiry,view.getExpireTime());
    }
    @Test void permanentRegrantRestoresSavedStatusAndHistory() {
        lesson.setStatus(LessonStatus.EXPIRED);
        when(rights.summaries(800L,List.of(10L))).thenReturn(Map.of(10L,new LearningEntitlementService.Summary(null,2)));
        var view=service.queryLearningRecordByCourse(10L);
        assertEquals(2,view.getStatus());assertNull(view.getExpireTime());
        assertEquals(4,view.getLearnedSections());assertEquals(20L,view.getId());
    }
}
