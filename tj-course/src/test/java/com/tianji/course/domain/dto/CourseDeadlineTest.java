package com.tianji.course.domain.dto;
import org.junit.jupiter.api.Test;import java.time.LocalDateTime;
import com.tianji.common.exceptions.BadRequestException;
import static org.junit.jupiter.api.Assertions.*;
class CourseDeadlineTest {
 CourseBaseInfoSaveDTO form(){var value=new CourseBaseInfoSaveDTO();value.setFree(true);value.setPrice(0);value.setPurchaseEndTime(LocalDateTime.now().minusDays(1));return value;}
 @Test void newCourseNeedsFutureDeadline(){assertThrows(BadRequestException.class,()->form().check());}
 @Test void existingClosedDraftCanKeepItsDeadline(){var value=form();value.setId(1L);assertDoesNotThrow(()->{value.check();});assertTrue(value.getPurchaseEndTime().isBefore(LocalDateTime.now()));}
}
