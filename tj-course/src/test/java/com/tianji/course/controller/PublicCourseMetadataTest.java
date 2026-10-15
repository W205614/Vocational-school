package com.tianji.course.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tianji.api.client.exam.ExamClient;
import com.tianji.api.client.learning.LearningClient;
import com.tianji.api.client.trade.TradeClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CatalogueDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.UnauthorizedException;
import com.tianji.common.utils.UserContext;
import com.tianji.course.domain.po.Course;
import com.tianji.course.domain.po.CourseCatalogue;
import com.tianji.course.mapper.CourseCatalogueMapper;
import com.tianji.course.mapper.CourseMapper;
import com.tianji.course.service.ICategoryService;
import com.tianji.course.service.ICourseTeacherService;
import com.tianji.course.service.impl.CourseCatalogueServiceImpl;
import com.tianji.course.service.impl.CourseServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicCourseMetadataTest {
    private final CourseMapper courses=mock(CourseMapper.class);
    private final CourseCatalogueMapper catalogues=mock(CourseCatalogueMapper.class);
    private final ExamClient exams=mock(ExamClient.class);
    private final UserClient users=mock(UserClient.class);
    private final TradeClient trade=mock(TradeClient.class);
    private final LearningClient learning=mock(LearningClient.class);
    private final ICourseTeacherService teachers=mock(ICourseTeacherService.class);
    private final CourseCatalogueServiceImpl directory=new CourseCatalogueServiceImpl();
    private PublicCourseController controller;

    @BeforeAll static void mapping() {
        if(TableInfoHelper.getTableInfo(CourseCatalogue.class)==null)
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"public-course-test"),CourseCatalogue.class);
    }
    @BeforeEach void setup() {
        UserContext.removeUser();
        ReflectionTestUtils.setField(directory,"baseMapper",catalogues);
        ReflectionTestUtils.setField(directory,"examClient",exams);
        var service=new CourseServiceImpl();ReflectionTestUtils.setField(service,"baseMapper",courses);
        ReflectionTestUtils.setField(service,"courseCatalogueService",directory);
        ReflectionTestUtils.setField(service,"courseTeacherService",teachers);
        ReflectionTestUtils.setField(service,"userClient",users);ReflectionTestUtils.setField(service,"examClient",exams);
        ReflectionTestUtils.setField(service,"tradeClient",trade);ReflectionTestUtils.setField(service,"learningClient",learning);
        controller=new PublicCourseController(service,mock(ICategoryService.class),directory);
    }
    @AfterEach void cleanup(){UserContext.removeUser();}

    @Test void anonymousDetailUsesOnlyLocalMetadataEvenWhenPrivateExamLookupWouldRejectIt() {
        var course=new Course().setId(42L).setName("Public course").setStatus(2).setDeleted(0).setPrice(1200).setFree(0).setDepId(7001L);
        when(courses.selectById(42L)).thenReturn(course);
        var chapter=node(10,0,1,1,"chapter");var video=node(20,10,2,2,"video");var exam=node(30,10,3,1,"test");
        when(catalogues.selectList(any())).thenReturn(List.of(video,chapter,exam));
        when(exams.queryQuestionIdsByBizIds(any())).thenThrow(new UnauthorizedException("Private exam lookup requires authentication"));
        var result=assertDoesNotThrow(()->controller.course(42));
        assertNull(UserContext.getUser());assertEquals("Public course",result.get("name"));
        assertFalse(result.containsKey("depId"));assertFalse(result.containsKey("teacherIds"));assertFalse(result.containsKey("records"));
        var chapters=(List<Map<String,Object>>)result.get("chapters");assertEquals(1,chapters.size());
        var children=(List<Map<String,Object>>)chapters.getFirst().get("sections");
        assertEquals(List.of("test","video"),children.stream().map(row->row.get("name")).toList());
        assertSafe(chapters);verifyNoInteractions(exams,users,trade,learning,teachers);
        var capture=ArgumentCaptor.forClass(LambdaQueryWrapper.class);verify(catalogues).selectList(capture.capture());
        var query=capture.getValue();
        assertEquals(Set.of("id","name","type","parent_catalogue_id","c_index","media_duration"),Set.of(query.getSqlSelect().split(",")));
        assertTrue(query.getSqlSegment().contains("course_id"));assertTrue(query.getSqlSegment().contains("deleted"));
        assertTrue(query.getParamNameValuePairs().containsValue(42L));assertTrue(query.getParamNameValuePairs().containsValue(0));
    }
    @Test void localDirectoryProjectionNeverCopiesPrivateMediaOrExamFields() {
        when(catalogues.selectList(any())).thenReturn(List.of(node(1,0,1,1,"chapter"),node(2,1,2,1,"section")));
        var tree=directory.queryPublicCourseCatalogues(42L);var section=tree.getFirst().getSections().getFirst();
        assertEquals("section",section.getName());assertNull(section.getMediaId());assertNull(section.getMediaName());
        assertNull(section.getTrailer());assertFalse(Boolean.TRUE.equals(section.getCanUpdate()));assertNull(section.getSubjectNum());verifyNoInteractions(exams);
    }
    @Test void deletedOrUnpublishedCourseCannotReachThePublicDirectoryOrPrivateServices() {
        var course=new Course().setId(42L).setName("Hidden course").setStatus(2).setDeleted(1);
        when(courses.selectById(42L)).thenReturn(course);assertThrows(BadRequestException.class,()->controller.course(42));
        course.setDeleted(0).setStatus(1);assertThrows(BadRequestException.class,()->controller.course(42));
        verifyNoInteractions(catalogues,exams,users,trade,learning,teachers);
    }
    private CourseCatalogue node(long id,long parent,int type,int index,String name) {
        return new CourseCatalogue().setId(id).setCourseId(42L).setParentCatalogueId(parent).setType(type).setCIndex(index).setName(name)
                .setDeleted(0).setMediaDuration(300).setMediaId(9001L).setVideoId(9002L).setVideoName("private-video.mp4").setTrailer(1).setCreater(8001L);
    }
    private void assertSafe(List<Map<String,Object>> nodes) {
        for(var node:nodes){assertEquals(Set.of("id","name","index","type","mediaDuration","sections"),node.keySet());assertFalse(node.toString().contains("private-video"));assertSafe((List<Map<String,Object>>)node.get("sections"));}
    }
}
