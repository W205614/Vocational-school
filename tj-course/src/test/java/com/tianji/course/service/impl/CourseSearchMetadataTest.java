package com.tianji.course.service.impl;

import com.tianji.api.client.trade.TradeClient;
import com.tianji.course.domain.po.Course;
import com.tianji.course.domain.po.CourseTeacher;
import com.tianji.course.mapper.CourseMapper;
import com.tianji.course.mapper.CourseTeacherMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CourseSearchMetadataTest {
 private final CourseMapper mapper=mock(CourseMapper.class);
 private final CourseTeacherMapper teachers=mock(CourseTeacherMapper.class);
 private final TradeClient trade=mock(TradeClient.class);
 private CourseServiceImpl service() {
  var service=new CourseServiceImpl();
  ReflectionTestUtils.setField(service,"baseMapper",mapper);
  ReflectionTestUtils.setField(service,"courseTeacherMapper",teachers);
  ReflectionTestUtils.setField(service,"tradeClient",trade);
  return service;
 }
 @Test void metadataCanBeRebuiltWhileFinancialServiceIsPaused() {
  var course=new Course();course.setId(42L);course.setName("Rebuild course");
  course.setStatus(2);course.setFirstCateId(1L);course.setSecondCateId(2L);course.setThirdCateId(3L);
  course.setSectionNum(8);course.setMediaDuration(480);course.setCreateTime(LocalDateTime.of(2026,10,8,12,0));
  when(mapper.selectById(42L)).thenReturn(course);
  var teacher=new CourseTeacher();teacher.setTeacherId(7L);
  when(teachers.selectList(any())).thenReturn(List.of(teacher));
  when(trade.countEnrollNumOfCourse(any())).thenThrow(new IllegalStateException("Financial service paused"));
  var metadata=assertDoesNotThrow(()->service().getCourseDTOById(42L));
  assertEquals("Rebuild course",metadata.getName());assertEquals(3L,metadata.getCategoryIdLv3());
  assertEquals(7L,metadata.getTeacher());assertEquals(8,metadata.getSections());
  assertEquals(course.getCreateTime(),metadata.getPublishTime());
  // Search sales are delivered separately by the versioned absolute projection.
  assertEquals(0,metadata.getSold());verifyNoInteractions(trade);
 }
 @Test void missingCourseReturnsTombstoneWithoutContactingFinancialService() {
  assertNull(service().getCourseDTOById(404L));verifyNoInteractions(teachers,trade);
 }
}
