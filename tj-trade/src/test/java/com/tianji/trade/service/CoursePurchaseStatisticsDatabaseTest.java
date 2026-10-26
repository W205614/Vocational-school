package com.tianji.trade.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.api.cache.RoleCache;
import com.tianji.api.client.user.UserClient;
import com.tianji.common.autoconfigure.mvc.advice.CommonExceptionAdvice;
import com.tianji.pay.sdk.client.PayClient;
import com.tianji.trade.controller.OrderDetailController;
import com.tianji.trade.mapper.*;
import com.tianji.trade.service.impl.OrderDetailServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real MySQL + actual annotated mapper + production purchase-statistics HTTP path. */
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class CoursePurchaseStatisticsDatabaseTest {
 @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
 @org.springframework.boot.autoconfigure.ImportAutoConfiguration({
  com.tianji.common.autoconfigure.mybatis.MybatisConfig.class,
  com.baomidou.mybatisplus.autoconfigure.MybatisPlusInnerInterceptorAutoConfiguration.class,
  com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class})
 static class FactoryConfiguration {}
 private JdbcTemplate jdbc;private long course,otherCourse;private MockMvc mvc;
 private org.springframework.context.ConfigurableApplicationContext context;
 private final JsonMapper json=JsonMapper.builder().findAndAddModules().build();
 @BeforeEach void setup()throws Exception {
  String base="jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/";
  String options="?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true";
  new JdbcTemplate(new DriverManagerDataSource(base+options,"root",System.getenv("ACCEPTANCE_DB_PASSWORD"))).execute("CREATE DATABASE IF NOT EXISTS acceptance_course_statistics");
  var source=new DriverManagerDataSource(base+"acceptance_course_statistics"+options,"root",System.getenv("ACCEPTANCE_DB_PASSWORD"));jdbc=new JdbcTemplate(source);
  assertEquals("acceptance_course_statistics",jdbc.queryForObject("SELECT DATABASE()",String.class));
  Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
  while(root!=null&&!Files.isRegularFile(root.resolve("deploy/compact/schema.sql")))root=root.getParent();assertNotNull(root);
  String schema=Files.readString(root.resolve("deploy/compact/schema.sql"));
  var table=Pattern.compile("CREATE TABLE `order_detail` \\(.*?\\) ENGINE=.*?;",Pattern.DOTALL).matcher(schema);
  assertTrue(table.find());jdbc.execute(table.group().replaceFirst("CREATE TABLE","CREATE TABLE IF NOT EXISTS"));
  course=IdWorker.getId();otherCourse=IdWorker.getId();
  var application=new org.springframework.context.annotation.AnnotationConfigApplicationContext();
  application.registerBean("dataSource",javax.sql.DataSource.class,()->source);
  org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment(application,"mybatis-plus.mapper-locations=classpath:/mapper/OrderDetailMapper.xml");
  application.register(FactoryConfiguration.class);application.refresh();context=application;
  var factory=context.getBean(SqlSessionFactory.class);
  if(!factory.getConfiguration().hasMapper(OrderDetailMapper.class))factory.getConfiguration().addMapper(OrderDetailMapper.class);
  var service=new OrderDetailServiceImpl(mock(UserClient.class),mock(OrderMapper.class),mock(PayClient.class),mock(RefundApplyMapper.class),mock(RoleCache.class));
  ReflectionTestUtils.setField(service,"baseMapper",new SqlSessionTemplate(factory).getMapper(OrderDetailMapper.class));
  mvc=MockMvcBuilders.standaloneSetup(new OrderDetailController(service)).setControllerAdvice(new CommonExceptionAdvice()).build();
 }
 @AfterEach void cleanup(){
  if(context!=null)context.close();
  if(jdbc!=null&&course>0)jdbc.update("DELETE FROM order_detail WHERE course_id IN (?,?)",course,otherCourse);
 }
 private void item(long courseId,int amount,int state){
  long id=IdWorker.getId();
  jdbc.update("INSERT INTO order_detail(id,order_id,user_id,course_id,price,name,cover_url,real_pay_amount,status,creater,updater) VALUES(?,?,7,?,?,'Synthetic statistics course','/brand-logo.png',?,?,7,7)",id,id,courseId,amount,amount,state);
 }
 private Map<?,?> statistics(long courseId)throws Exception {
  String body=mvc.perform(get("/order-details/purchaseInfo").param("courseId",Long.toString(courseId)))
   .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  return json.readValue(body,Map.class);
 }
 @Test void newlyPublishedCourseWithNoOrdersReturnsZeroStatisticsInsteadOfPrimitiveNullFailure()throws Exception {
  item(otherCourse,999999,2);
  var result=statistics(course);
  assertEquals(0,((Number)result.get("realPayAmount")).intValue());
  assertEquals(0,((Number)result.get("enrollNum")).intValue());
  assertEquals(0,((Number)result.get("refundNum")).intValue());
 }
 @Test void nonemptyCourseKeepsIntegerCentSumAndZeroCostEnrollmentWithoutIncludingOtherCourse()throws Exception {
  item(course,12500,2);item(course,7200,4);item(course,0,5);
  item(otherCourse,999999,2);
  var result=statistics(course);
  assertEquals(19700,((Number)result.get("realPayAmount")).intValue());
  assertEquals(3,((Number)result.get("enrollNum")).intValue());
  assertEquals(0,((Number)result.get("refundNum")).intValue());
 }
}
