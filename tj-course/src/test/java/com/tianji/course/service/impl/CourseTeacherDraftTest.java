package com.tianji.course.service.impl;

import com.tianji.course.domain.dto.CourseTeacherSaveDTO;
import com.tianji.course.domain.po.CourseTeacherDraft;
import com.tianji.course.mapper.CourseTeacherDraftMapper;
import com.tianji.course.service.ICourseDraftService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CourseTeacherDraftTest {
    @Test void sameTeacherCanBeAssociatedWithDifferentCoursesWithoutReusingRelationPrimaryKey() {
        var service = spy(new CourseTeacherDraftServiceImpl());
        ReflectionTestUtils.setField(service, "baseMapper", mock(CourseTeacherDraftMapper.class));
        ReflectionTestUtils.setField(service, "courseDraftService", mock(ICourseDraftService.class));
        List<CourseTeacherDraft> saved = new ArrayList<>();
        doAnswer(call -> { saved.addAll(call.<Collection<CourseTeacherDraft>>getArgument(0)); return true; })
                .when(service).saveBatch(any(Collection.class));
        var teacher = new CourseTeacherSaveDTO.TeacherInfo();
        teacher.setId(800000000000000003L);
        teacher.setIsShow(true);
        for (long courseId : List.of(10L, 11L)) {
            var form = new CourseTeacherSaveDTO();
            form.setId(courseId);
            form.setTeachers(List.of(teacher));
            service.save(form);
        }
        assertEquals(2, saved.size());
        for (var relation : saved) {
            assertNull(relation.getId());
            assertEquals(teacher.getId(), relation.getTeacherId());
            assertEquals(1, relation.getIsShow());
        }
        assertNotEquals(saved.get(0).getCourseId(), saved.get(1).getCourseId());
    }
}
