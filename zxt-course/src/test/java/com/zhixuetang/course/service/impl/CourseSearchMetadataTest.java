package com.zhixuetang.course.service.impl;

import com.zhixuetang.api.client.trade.TradeClient;
import com.zhixuetang.course.domain.po.Course;
import com.zhixuetang.course.domain.po.CourseTeacher;
import com.zhixuetang.course.mapper.CourseMapper;
import com.zhixuetang.course.mapper.CourseTeacherMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CourseSearchMetadataTest {
    private final CourseMapper mapper = mock(CourseMapper.class);
    private final CourseTeacherMapper teachers = mock(CourseTeacherMapper.class);
    private final TradeClient trade = mock(TradeClient.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private CourseServiceImpl service() {
        var service = new CourseServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "courseTeacherMapper", teachers);
        ReflectionTestUtils.setField(service, "tradeClient", trade);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        return service;
    }

    @Test
    void metadataCanBeRebuiltWhileFinancialServiceIsPaused() throws Exception {
        var course = new Course();
        course.setId(42L);
        course.setName("Rebuild course");
        course.setStatus(2);
        course.setFirstCateId(1L);
        course.setSecondCateId(2L);
        course.setThirdCateId(3L);
        course.setSectionNum(8);
        course.setMediaDuration(480);
        course.setCreateTime(LocalDateTime.of(2026, 10, 8, 12, 0));
        when(mapper.selectById(42L)).thenReturn(course);
        var teacher = new CourseTeacher();
        teacher.setTeacherId(7L);
        when(teachers.selectList(any())).thenReturn(List.of(teacher));
        when(trade.countEnrollNumOfCourse(any()))
                .thenThrow(new IllegalStateException("Financial service paused"));
        var study = mock(ResultSet.class);
        when(study.getString("difficulty")).thenReturn("INTERMEDIATE");
        when(study.getObject("minutes", Integer.class)).thenReturn(42);
        when(study.getBoolean("has_trial")).thenReturn(true);
        doAnswer(call -> {
            call.<RowCallbackHandler>getArgument(1).processRow(study);
            return null;
        }).when(jdbc).query(anyString(), any(RowCallbackHandler.class), eq(42L), eq(42L));
        var metadata = assertDoesNotThrow(() -> service().getCourseDTOById(42L));
        assertEquals("Rebuild course", metadata.getName());
        assertEquals(3L, metadata.getCategoryIdLv3());
        assertEquals(7L, metadata.getTeacher());
        assertEquals(8, metadata.getSections());
        assertEquals(course.getCreateTime(), metadata.getPublishTime());
        assertEquals("INTERMEDIATE", metadata.getDifficulty());
        assertEquals(42, metadata.getEstimatedStudyMinutes());
        assertTrue(metadata.getHasTrial());
        // Search sales are delivered separately by the versioned absolute projection.
        assertEquals(0, metadata.getSold());
        verifyNoInteractions(trade);
    }

    @Test
    void missingCourseReturnsTombstoneWithoutContactingFinancialService() {
        assertNull(service().getCourseDTOById(404L));
        verifyNoInteractions(teachers, trade, jdbc);
    }
}
