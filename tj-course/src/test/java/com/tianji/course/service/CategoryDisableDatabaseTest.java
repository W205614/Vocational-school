package com.tianji.course.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.tianji.common.utils.UserContext;
import com.tianji.course.domain.dto.CategoryDisableOrEnableDTO;
import com.tianji.course.domain.po.Course;
import com.tianji.course.mapper.CategoryMapper;
import com.tianji.course.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class CategoryDisableDatabaseTest {
    private JdbcTemplate jdbc;private ICategoryService categories;private long category,first,second;
    private final ICourseService courses=mock(ICourseService.class);
    private final ICourseDraftService drafts=mock(ICourseDraftService.class);

    @BeforeEach void setup()throws Exception{
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_common","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
        jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE IF NOT EXISTS category(id BIGINT PRIMARY KEY,name VARCHAR(100),parent_id BIGINT,level INT,priority INT,status INT,create_time DATETIME,update_time DATETIME,creater BIGINT,updater BIGINT,deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS category_disable_course_fixture(id BIGINT PRIMARY KEY,status INT)");
        category=IdWorker.getId();first=category+1;second=category+2;
        jdbc.update("INSERT INTO category(id,parent_id,level,status,deleted) VALUES(?,0,1,1,0)",category);
        jdbc.update("INSERT INTO category_disable_course_fixture VALUES(?,2),(?,2)",first,second);
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);configuration.addMapper(CategoryMapper.class);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(configuration);
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/CouponCalculate.xml"));factory.afterPropertiesSet();
        var session=new SqlSessionTemplate(factory.getObject());
        var target=new CategoryServiceImpl();ReflectionTestUtils.setField(target,"baseMapper",session.getMapper(CategoryMapper.class));
        ReflectionTestUtils.setField(target,"courseService",courses);ReflectionTestUtils.setField(target,"courseDraftService",drafts);
        var proxy=new ProxyFactory(target);proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),new AnnotationTransactionAttributeSource()));
        categories=(ICategoryService)proxy.getProxy();
        when(courses.queryByCategoryIdAndLevel(category,1)).thenReturn(List.of(new Course().setId(first).setStatus(2),new Course().setId(second).setStatus(2),new Course().setId(category+3).setStatus(1)));
        doAnswer(call->{jdbc.update("UPDATE category_disable_course_fixture SET status=3 WHERE id=?",(Long)call.getArgument(0));return null;}).when(drafts).downShelf(anyLong());
        UserContext.setUser(7L);
    }
    @AfterEach void cleanup(){
        UserContext.removeUser();jdbc.update("DELETE FROM category_disable_course_fixture WHERE id IN(?,?)",first,second);jdbc.update("DELETE FROM category WHERE id=?",category);
    }
    @Test void successfulDisableCompletesPublishedCourseChangesBeforeReturning(){
        var form=new CategoryDisableOrEnableDTO();form.setId(category);form.setStatus(0);categories.disableOrEnable(form);
        assertEquals(0,jdbc.queryForObject("SELECT status FROM category WHERE id=?",Integer.class,category));
        assertEquals(List.of(3,3),jdbc.queryForList("SELECT status FROM category_disable_course_fixture WHERE id IN(?,?) ORDER BY id",Integer.class,first,second));
        verify(drafts,never()).downShelf(category+3);assertEquals(7L,UserContext.getUser());
    }
    @Test void courseFailureRollsBackCategoryAndEarlierCourseChanges(){
        var failure=new DataAccessResourceFailureException("fixture database unavailable");doThrow(failure).when(drafts).downShelf(second);
        var form=new CategoryDisableOrEnableDTO();form.setId(category);form.setStatus(0);
        assertSame(failure,assertThrows(DataAccessResourceFailureException.class,()->categories.disableOrEnable(form)));
        assertEquals(1,jdbc.queryForObject("SELECT status FROM category WHERE id=?",Integer.class,category));
        assertEquals(List.of(2,2),jdbc.queryForList("SELECT status FROM category_disable_course_fixture WHERE id IN(?,?) ORDER BY id",Integer.class,first,second));
    }
    @Test void enablingDoesNotDownShelfCourses(){
        var form=new CategoryDisableOrEnableDTO();form.setId(category);form.setStatus(1);categories.disableOrEnable(form);verifyNoInteractions(courses,drafts);
    }
}
