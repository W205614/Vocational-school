package com.tianji.learning.reliability;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.api.client.course.*;
import com.tianji.common.autoconfigure.mvc.advice.CommonExceptionAdvice;
import com.tianji.learning.config.MyBatisConfiguration;
import com.tianji.learning.controller.LearningLessonController;
import com.tianji.learning.mapper.*;
import com.tianji.learning.service.impl.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP route binding and real MyBatis COUNT on isolated MySQL; no mocked statistics. */
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class LearningLessonCountDatabaseTest {
    private JdbcTemplate jdbc;private long course,otherCourse;private MockMvc mvc;
    private AnnotationConfigApplicationContext context;
    @BeforeEach void setup() {
        String url=System.getenv().getOrDefault("ACCEPTANCE_LEARNING_DB_URL","jdbc:mysql://127.0.0.1:"+
                System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_learning?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true");
        assertFalse(url.contains(":24316/"),"Daily database must never be used for acceptance fixtures");
        assertNotNull(System.getenv("TJ_INTERNAL_TOKEN"),"Tests require the isolated service credential");
        var source=new DriverManagerDataSource(url,"root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
        jdbc=new JdbcTemplate(source);
        assertTrue(jdbc.queryForObject("SELECT DATABASE()",String.class).matches("acceptance_learning(?:_media)?"));
        course=IdWorker.getId();otherCourse=IdWorker.getId();
        context=new AnnotationConfigApplicationContext();context.registerBean("dataSource",javax.sql.DataSource.class,()->source);
        org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "mybatis-plus.mapper-locations=classpath:/mapper/LearningLessonMapper.xml,classpath:/mapper/LearningRecordMapper.xml");
        context.register(LearningLessonPaginationTest.FactoryConfiguration.class,MyBatisConfiguration.class);context.refresh();
        var factory=context.getBean(SqlSessionFactory.class);var config=factory.getConfiguration();
        if(!config.hasMapper(LearningLessonMapper.class))config.addMapper(LearningLessonMapper.class);
        if(!config.hasMapper(LearningRecordMapper.class))config.addMapper(LearningRecordMapper.class);
        var session=new SqlSessionTemplate(factory);
        var service=new LearningLessonServiceImpl(mock(CourseClient.class),mock(CatalogueClient.class),
                session.getMapper(LearningRecordMapper.class),jdbc,new LearningEntitlementService(jdbc));
        ReflectionTestUtils.setField(service,"baseMapper",session.getMapper(LearningLessonMapper.class));
        mvc=MockMvcBuilders.standaloneSetup(new LearningLessonController(service)).setControllerAdvice(new CommonExceptionAdvice()).build();
    }
    @AfterEach void cleanup() {
        if(context!=null)context.close();
        if(jdbc!=null && course>0)jdbc.update("DELETE FROM learning_lesson WHERE course_id IN (?,?)",course,otherCourse);
    }
    @Test void missingOrIncorrectServiceCredentialsAreRejectedOnBothRoutes()throws Exception {
        for(String path:paths()) {
            mvc.perform(get(path)).andExpect(status().isForbidden());
            mvc.perform(get(path).header("X-Internal-Token","untrusted")).andExpect(status().isForbidden());
            // The gateway adds its own trusted token; that must not turn this RPC into a public endpoint.
            mvc.perform(get(path).header("X-Internal-Token",System.getenv("TJ_INTERNAL_TOKEN")).header("x-request-from","gateway"))
                    .andExpect(status().isForbidden());
        }
    }
    @Test void emptyNewCourseReturnsZeroOnClientAndLegacyCompatibilityRoutes()throws Exception {
        for(String path:paths())mvc.perform(get(path).header("X-Internal-Token",System.getenv("TJ_INTERNAL_TOKEN")))
                .andExpect(status().isOk()).andExpect(content().string("0"));
    }
    @Test void countPreservesAllHistoricalLessonStatesAndFiltersByCourse()throws Exception {
        for(int state=0;state<4;state++)insert(course,state);
        insert(otherCourse,1);
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM learning_lesson WHERE course_id=?",Integer.class,course));
        for(String path:paths())mvc.perform(get(path).header("X-Internal-Token",System.getenv("TJ_INTERNAL_TOKEN")))
                .andExpect(status().isOk()).andExpect(content().string("4"));
    }
    private void insert(long courseId,int state) {
        jdbc.update("INSERT INTO learning_lesson(id,user_id,course_id,status,learned_sections) VALUES(?,?,?,?,0)",IdWorker.getId(),IdWorker.getId(),courseId,state);
    }
    private String[] paths(){return new String[]{"/lessons/"+course+"/count","/lessons/lessons/"+course+"/count"};}
}
