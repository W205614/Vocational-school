package com.tianji.search.reliability;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.search.config.*;
import com.tianji.search.domain.po.Course;
import com.tianji.search.domain.query.CoursePageQuery;
import com.tianji.search.repository.impl.CourseRepositoryImpl;
import com.tianji.search.service.IInterestsService;
import com.tianji.search.service.impl.SearchServiceImpl;
import com.tianji.common.utils.UserContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_ES_URL",matches=".+")
class SearchCompatibilityTest {
 @Test void publicSearchFiltersPaginationRecommendationsAndSafeHighlightsRemainCompatible()throws Exception {
  try(var transport=new SearchConnectionConfiguration().searchTransport(System.getenv("ACCEPTANCE_ES_URL"))) {
   var client=new ElasticsearchClient(transport);var repository=new CourseRepositoryImpl(client);var service=new SearchServiceImpl();
   long id=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();String keyword="contract"+id;var now=LocalDateTime.now().withNano(0);
   var teachers=mock(UserClient.class);var teacher=new UserDTO();teacher.setId(id+100);teacher.setName("合成教师");when(teachers.queryUserByIds(any())).thenReturn(List.of(teacher));
   var interests=mock(IInterestsService.class);when(interests.queryMyInterestsIds()).thenReturn(List.of(id+10));
   var properties=new InterestsProperties();properties.setTopNumber(3);
   ReflectionTestUtils.setField(service,"restClient",client);ReflectionTestUtils.setField(service,"userClient",teachers);
   ReflectionTestUtils.setField(service,"interestsService",interests);ReflectionTestUtils.setField(service,"interestsProperties",properties);
   var ids=List.of(id,id+1,id+2,id+3);
   try {
    for(int index=0;index<3;index++) {
     var course=new Course();course.setId(id+index);course.setName(keyword+" Java <img src=x onerror=bad>");course.setTeacher(id+100);
     course.setCategoryIdLv1(id+9);course.setCategoryIdLv2(id+10);course.setCategoryIdLv3(id+11);
     course.setPrice(index==2?0:300-index*200);course.setFree(index==2);course.setType(1);course.setPublishTime(now.plusDays(index));course.setUpdateTime(now);
     repository.projectMetadata(course.getId(),course,1);repository.updateById(course.getId(),"sold",index+1);
    }
    repository.projectMetadata(id+3,null,1);client.indices().refresh(r->r.index("course"));
    var query=new CoursePageQuery();query.setKeyword(keyword);query.setPageNo(1);query.setPageSize(10);query.setSortBy("price");query.setIsAsc(true);
    var page=service.queryCoursesForPortal(query);assertEquals(3,page.getTotal());assertEquals(id+2,page.getList().getFirst().getId());assertEquals("合成教师",page.getList().getFirst().getTeacher());
    assertTrue(page.getList().getFirst().getName().contains("&lt;img"));assertFalse(page.getList().getFirst().getName().contains("<img"));
    query.setFree(true);assertEquals(1,service.queryCoursesForPortal(query).getTotal());query.setFree(null);
    query.setCategoryIdLv2(id+20);assertEquals(0,service.queryCoursesForPortal(query).getTotal());query.setCategoryIdLv2(id+10);
    query.setType(2);assertEquals(0,service.queryCoursesForPortal(query).getTotal());query.setType(1);
    query.setBeginTime(now.minusSeconds(1));query.setEndTime(now.plusSeconds(1));assertEquals(3,service.queryCoursesForPortal(query).getTotal());
    query.setPageSize(1);query.setPageNo(2);assertEquals(3,service.queryCoursesForPortal(query).getPages());assertEquals(1,service.queryCoursesForPortal(query).getList().size());
    assertEquals(new HashSet<>(ids.subList(0,3)),new HashSet<>(service.queryCoursesIdByName(keyword)));
    UserContext.setUser(id+50);assertEquals(id+1,service.queryBestTopN().getFirst().getId());assertEquals(id+2,service.queryFreeTopN().getFirst().getId());assertEquals(3,service.queryCourseByCateId(id+10).size());
    query.setSortBy("untrusted-sort");assertThrows(com.tianji.common.exceptions.BadRequestException.class,()->service.queryCoursesForPortal(query));
   }finally{UserContext.removeUser();repository.deleteByIds(ids);}
  }
 }
}
