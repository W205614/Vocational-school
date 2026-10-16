package com.tianji.learning.reliability;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.api.client.course.*;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.config.MyBatisConfiguration;
import com.tianji.learning.controller.LearningLessonController;
import com.tianji.learning.mapper.*;
import com.tianji.learning.service.impl.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real MySQL + Boot auto-configuration + controller HTTP binding. Course RPC is a bounded contract double. */
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class LearningLessonPaginationTest {
    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    @org.springframework.boot.autoconfigure.ImportAutoConfiguration({
            com.tianji.common.autoconfigure.mybatis.MybatisConfig.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusInnerInterceptorAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class})
    static class FactoryConfiguration {}
    private JdbcTemplate jdbc;private long user,other;private List<Long> expected;private MockMvc mvc;
    private final List<Integer> courseBatchSizes=new ArrayList<>();
    private org.springframework.context.ConfigurableApplicationContext context;
    private final JsonMapper json=JsonMapper.builder().findAndAddModules().build();
    @BeforeEach void prepare() {
        String port=System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316");
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+port+"/acceptance_learning?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
        jdbc=new JdbcTemplate(source);assertEquals("acceptance_learning",jdbc.queryForObject("SELECT DATABASE()",String.class));
        user=IdWorker.getId();other=IdWorker.getId();expected=new ArrayList<>();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_lesson WHERE user_id IN (?,?)",Integer.class,user,other));
        for(int n=0;n<124;n++) {
            long id=IdWorker.getId(),course=IdWorker.getId();
            jdbc.update("INSERT INTO learning_lesson(id,user_id,course_id,status,week_freq,plan_status,learned_sections,latest_learn_time) VALUES(?,?,?,1,2,1,0,?)",id,n<121?user:other,course,LocalDateTime.of(2026,1,1,10,0));
            if(n<121)expected.add(id);
        }
        expected.sort(Comparator.reverseOrder());
        // Keep the real context alive for requests instead of injecting a handcrafted SQL plugin chain.
        var application=new org.springframework.context.annotation.AnnotationConfigApplicationContext();
        application.registerBean("dataSource",javax.sql.DataSource.class,()->source);
        org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment(application,"mybatis-plus.mapper-locations=classpath:/mapper/LearningLessonMapper.xml,classpath:/mapper/LearningRecordMapper.xml");
        application.register(FactoryConfiguration.class,MyBatisConfiguration.class);application.refresh();context=application;
        var factory=context.getBean(SqlSessionFactory.class);var config=factory.getConfiguration();
        if(!config.hasMapper(LearningLessonMapper.class))config.addMapper(LearningLessonMapper.class);
        if(!config.hasMapper(LearningRecordMapper.class))config.addMapper(LearningRecordMapper.class);
        var session=new SqlSessionTemplate(factory);var courses=mock(CourseClient.class);
        when(courses.getSimpleInfoList(any())).thenAnswer(call->{
            List<Long> ids=new ArrayList<>();((Iterable<Long>)call.getArgument(0)).forEach(ids::add);courseBatchSizes.add(ids.size());
            assertTrue(ids.size()<=100,"The actual course metadata contract permits at most 100 IDs");
            return ids.stream().map(id->{var dto=new CourseSimpleInfoDTO();dto.setId(id);dto.setName("Page course "+id);dto.setSectionNum(20);return dto;}).toList();
        });
        var service=new LearningLessonServiceImpl(courses,mock(CatalogueClient.class),session.getMapper(LearningRecordMapper.class),jdbc,new LearningEntitlementService(jdbc));
        ReflectionTestUtils.setField(service,"baseMapper",session.getMapper(LearningLessonMapper.class));
        mvc=MockMvcBuilders.standaloneSetup(new LearningLessonController(service)).build();UserContext.setUser(user);
    }
    @AfterEach void cleanup(){UserContext.removeUser();if(context!=null)context.close();if(jdbc!=null && user>0)jdbc.update("DELETE FROM learning_lesson WHERE user_id IN (?,?)",user,other);}
    @SuppressWarnings("unchecked") private Map<String,Object> request(String endpoint,int page,int size)throws Exception {
        var raw=mvc.perform(get(endpoint).param("pageNo",Integer.toString(page)).param("pageSize",Integer.toString(size))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readValue(raw,Map.class);
    }
    private List<Long> ids(Map<String,Object> response) {
        if(response.get("list")==null)return List.of();
        return ((List<?>)response.get("list")).stream().map(row->((Number)((Map<?,?>)row).get("id")).longValue()).toList();
    }
    @Test void lessonHttpPagesRespectTwentyAndHundredWithoutDuplicatesOrTenantLeaks()throws Exception {
        var first=request("/lessons/page",1,20);var second=request("/lessons/page",2,20);
        assertEquals(121,((Number)first.get("total")).intValue());assertEquals(7,((Number)first.get("pages")).intValue());
        assertEquals(expected.subList(0,20),ids(first));assertEquals(expected.subList(20,40),ids(second));
        assertTrue(Collections.disjoint(ids(first),ids(second)));
        var hundred=request("/lessons/page",1,100);var tail=request("/lessons/page",2,100);var empty=request("/lessons/page",3,100);
        assertEquals(expected.subList(0,100),ids(hundred));assertEquals(expected.subList(100,121),ids(tail));assertTrue(ids(empty).isEmpty());
        assertEquals(121,((Number)empty.get("total")).intValue());assertEquals(2,((Number)empty.get("pages")).intValue());
        assertEquals(List.of(20,20,100,21),courseBatchSizes);
    }
    @Test void planHttpPagesAndStatisticsUseTheSameBoundedOrderedStudentScope()throws Exception {
        var first=request("/lessons/plans",1,20);var second=request("/lessons/plans",2,20);var tail=request("/lessons/plans",2,100);
        assertEquals(121,((Number)first.get("total")).intValue());assertEquals(7,((Number)first.get("pages")).intValue());
        assertEquals(242,((Number)first.get("weekTotalPlan")).intValue());assertEquals(0,((Number)first.get("weekFinished")).intValue());
        assertEquals(expected.subList(0,20),ids(first));assertEquals(expected.subList(20,40),ids(second));assertEquals(expected.subList(100,121),ids(tail));
        assertTrue(Collections.disjoint(ids(first),ids(second)));assertEquals(List.of(20,20,21),courseBatchSizes);
    }
}
