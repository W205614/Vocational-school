package com.tianji.learning.reliability;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.common.exceptions.*;
import com.tianji.learning.service.impl.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LearningMotivationTest {
    @Test void monthValidationAndLeapMonthFreezeUseNaturalCalendar() {
        assertEquals(YearMonth.of(2024,2),LearningSeasonService.parseMonth("202402"));
        assertThrows(BadRequestException.class,()->LearningSeasonService.parseMonth("202413"));
        assertThrows(BadRequestException.class,()->LearningSeasonService.parseMonth("2024-02"));
        var jdbc=mock(JdbcTemplate.class);LearningSeasonService.ensureSeason(jdbc,YearMonth.of(2024,2));
        verify(jdbc).update(anyString(),eq("202402"),eq("2024年2月赛季"),eq(LocalDateTime.of(2024,3,2,0,0)));
    }
    @Test void teacherNeedsActualCourseAssignmentAndStudentCannotAdopt() {
        var courses=mock(CourseClient.class);var info=new CourseFullInfoDTO();info.setTeacherIds(List.of(10L));when(courses.getCourseInfoById(7L,false,true)).thenReturn(info);
        var handler=new LearningHubOperationHandler(null,null,courses,null,null,null,null,Clock.systemUTC());
        assertDoesNotThrow(()->handler.requireTeacher(10,3L,7));
        assertThrows(ForbiddenException.class,()->handler.requireTeacher(11,3L,7));
        assertThrows(ForbiddenException.class,()->handler.requireTeacher(10,2L,7));
        assertDoesNotThrow(()->handler.requireTeacher(11,1L,7));
    }
    @Test void milestoneReplaysCannotAwardPointsOrAchievementsAgain() {
        var jdbc=mock(JdbcTemplate.class);var points=mock(PointsRecordServiceImpl.class);var clock=Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"),ZoneId.of("Asia/Shanghai"));
        var service=new LearningMilestoneService(jdbc,points,clock);service.completed(1,2,3,true,LocalDateTime.now(clock));
        verifyNoInteractions(points);
    }
    @Test void startedSeasonRewardRulesCannotBeChanged() {
        var clock=Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"),ZoneId.of("Asia/Shanghai"));var json=tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
        var handler=new LearningHubOperationHandler(null,json,null,null,null,null,null,clock);
        var cmd=new LearningHubOperationHandler.Command("RULE",1L,null,null,1L,"202610",1,3,10L,true,0L,null,null);
        assertThrows(BadRequestException.class,()->handler.execute("test",1,json.writeValueAsString(cmd)));
    }
}
