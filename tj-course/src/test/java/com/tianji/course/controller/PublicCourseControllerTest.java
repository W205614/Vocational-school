package com.tianji.course.controller;

import com.tianji.api.dto.course.*;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.UserContext;
import com.tianji.course.service.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicCourseControllerTest {
    private final ICourseService courses=mock(ICourseService.class);
    private final ICategoryService categories=mock(ICategoryService.class);
    private final PublicCourseController controller=new PublicCourseController(courses,categories);
    @AfterEach void cleanup(){UserContext.removeUser();}
    @Test void anonymousCourseMetadataUsesExplicitSafeProjectionAtEveryCatalogueDepth(){
        UserContext.removeUser();var course=new CourseFullInfoDTO();course.setId(42L);course.setName("Java course");course.setStatus(2);course.setPrice(1200);course.setFree(false);course.setSectionNum(1);course.setTeacherIds(List.of(9999L));
        var chapter=node(1L,"chapter",1,8001L);var section=node(2L,"section",2,8002L);var nested=node(3L,"nested",2,8003L);section.setSections(List.of(nested));chapter.setSections(List.of(section));course.setChapters(List.of(chapter));
        when(courses.getInfoById(42L,true,false)).thenReturn(course);
        var result=assertDoesNotThrow(()->controller.course(42));assertEquals(42L,result.get("id"));assertEquals("Java course",result.get("name"));assertEquals(1200,result.get("price"));assertFalse(result.containsKey("teacherIds"));
        var chapters=(List<Map<String,Object>>)result.get("chapters");assertEquals(1,chapters.size());assertSafe(chapters);
        assertEquals("section",((List<Map<String,Object>>)chapters.getFirst().get("sections")).getFirst().get("name"));verify(courses).getInfoById(42L,true,false);assertNull(UserContext.getUser());
    }
    @Test void absentDraftPausedAndUnpublishedCoursesCannotBeBrowsed(){
        when(courses.getInfoById(42L,true,false)).thenReturn(null);assertThrows(BadRequestException.class,()->controller.course(42));
        for(Integer status:Arrays.asList(null,0,1,3)){
            var course=new CourseFullInfoDTO();course.setStatus(status);course.setName("private draft");when(courses.getInfoById(42L,true,false)).thenReturn(course);assertThrows(BadRequestException.class,()->controller.course(42));
        }
    }
    @Test void publishedCourseWithoutCatalogueReturnsAnEmptyPublicList(){
        var course=new CourseFullInfoDTO();course.setId(42L);course.setStatus(2);when(courses.getInfoById(42L,true,false)).thenReturn(course);assertEquals(List.of(),controller.course(42).get("chapters"));
    }
    @Test void finishedCourseKeepsItsPublicDescriptionWithoutReopeningEnrollment(){
        var course=new CourseFullInfoDTO();course.setId(42L);course.setStatus(4);course.setName("Finished course");
        course.setPurchaseEndTime(java.time.LocalDateTime.of(2020,1,1,0,0));when(courses.getInfoById(42L,true,false)).thenReturn(course);
        var result=controller.course(42);assertEquals(4,result.get("status"));assertEquals(course.getPurchaseEndTime(),result.get("purchaseEndTime"));
        assertFalse(result.containsKey("teacherIds"));assertFalse(result.containsKey("mediaId"));
    }
    private CatalogueDTO node(long id,String name,int type,long media){var node=new CatalogueDTO();node.setId(id);node.setName(name);node.setType(type);node.setIndex(1);node.setMediaDuration(300);node.setMediaId(media);node.setMediaName("private-file.mp4");node.setTrailer(true);node.setCanUpdate(true);return node;}
    private void assertSafe(List<Map<String,Object>> nodes){for(var node:nodes){assertEquals(Set.of("id","name","index","type","mediaDuration","sections"),node.keySet());assertFalse(node.containsKey("mediaId"));assertFalse(node.containsKey("mediaName"));assertFalse(node.containsKey("canUpdate"));assertSafe((List<Map<String,Object>>)node.get("sections"));}}
}
