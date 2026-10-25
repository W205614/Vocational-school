package com.tianji.user.reliability;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.api.cache.RoleCache;
import com.tianji.common.enums.UserType;
import com.tianji.user.domain.query.UserPageQuery;
import com.tianji.user.domain.vo.StaffVO;
import com.tianji.user.mapper.UserDetailMapper;
import com.tianji.user.service.impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual joined MySQL pagination must include teachers and staff, while excluding students. */
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class StaffDirectoryDatabaseTest {
 @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
 @org.springframework.boot.autoconfigure.ImportAutoConfiguration({
  com.tianji.common.autoconfigure.mybatis.MybatisConfig.class,
  com.baomidou.mybatisplus.autoconfigure.MybatisPlusInnerInterceptorAutoConfiguration.class,
  com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class})
 static class FactoryConfiguration {}
 private JdbcTemplate jdbc;private UserDetailServiceImpl details;private StaffServiceImpl staff;
 private org.springframework.context.ConfigurableApplicationContext context;
 private final List<Long> ids=new ArrayList<>();private String prefix,teacherPhone;
 @BeforeEach void setup()throws Exception {
  String base="jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/";
  String options="?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true";
  new JdbcTemplate(new DriverManagerDataSource(base+options,"root",System.getenv("ACCEPTANCE_DB_PASSWORD"))).execute("CREATE DATABASE IF NOT EXISTS acceptance_staff_directory");
  var source=new DriverManagerDataSource(base+"acceptance_staff_directory"+options,"root",System.getenv("ACCEPTANCE_DB_PASSWORD"));jdbc=new JdbcTemplate(source);
  assertEquals("acceptance_staff_directory",jdbc.queryForObject("SELECT DATABASE()",String.class));
  Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
  while(root!=null&&!Files.isRegularFile(root.resolve("deploy/compact/schema.sql")))root=root.getParent();assertNotNull(root);
  String schema=Files.readString(root.resolve("deploy/compact/schema.sql"));
  for(String table:List.of("user","user_detail")){
   var definition=Pattern.compile("CREATE TABLE `"+table+"` \\(.*?\\) ENGINE=.*?;",Pattern.DOTALL).matcher(schema);
   assertTrue(definition.find());jdbc.execute(definition.group().replaceFirst("CREATE TABLE","CREATE TABLE IF NOT EXISTS"));
  }
  prefix="dir-"+IdWorker.getId()+"-";
  int[] types={3,1,3,3,2};
  for(int n=0;n<types.length;n++){
   long id=IdWorker.getId();ids.add(id);String phone=String.format("130%08d",Math.floorMod(id,100000000L));if(n==0)teacherPhone=phone;
   jdbc.update("INSERT INTO `user`(id,username,cell_phone,password,type,status,create_time) VALUES(?,?,?,'synthetic-unusable',?,?,?)",id,"dir-"+id,phone,types[n],n==3?0:1,LocalDateTime.of(2026,10,26,0,0,n));
   jdbc.update("INSERT INTO user_detail(id,type,name,role_id) VALUES(?,?,?,?)",id,types[n],prefix+n,types[n]==1?9:types[n]);
  }
  var application=new org.springframework.context.annotation.AnnotationConfigApplicationContext();
  application.registerBean("dataSource",javax.sql.DataSource.class,()->source);
  org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment(application,"mybatis-plus.mapper-locations=classpath:/mapper/UserDetailMapper.xml");
  application.register(FactoryConfiguration.class);application.refresh();context=application;
  var factory=context.getBean(SqlSessionFactory.class);if(!factory.getConfiguration().hasMapper(UserDetailMapper.class))factory.getConfiguration().addMapper(UserDetailMapper.class);
  details=new UserDetailServiceImpl();ReflectionTestUtils.setField(details,"baseMapper",new SqlSessionTemplate(factory).getMapper(UserDetailMapper.class));
  var roles=mock(RoleCache.class);when(roles.getRoleName(9L)).thenReturn("运营员工");staff=new StaffServiceImpl(details,roles);
 }
 @AfterEach void cleanup(){
  if(context!=null)context.close();if(jdbc!=null)for(long id:ids){jdbc.update("DELETE FROM user_detail WHERE id=?",id);jdbc.update("DELETE FROM `user` WHERE id=?",id);}
 }
 private UserPageQuery query(){var query=new UserPageQuery();query.setName(prefix);query.setPageSize(2);return query;}
 private List<Long> resultIds(List<StaffVO> rows){return rows.stream().map(StaffVO::getId).toList();}
 @Test void teacherAndStaffShareOneCorrectlyCountedPageAndStudentsStayExcluded(){
  var query=query();var first=staff.queryStaffPage(query);assertEquals(4L,first.getTotal());assertEquals(2L,first.getPages());assertEquals(List.of(ids.get(3),ids.get(2)),resultIds(first.getList()));
  assertTrue(first.getList().stream().allMatch(row->"教师".equals(row.getRoleName())));
  query.setPageNo(2);var second=staff.queryStaffPage(query);assertEquals(List.of(ids.get(1),ids.get(0)),resultIds(second.getList()));assertEquals("运营员工",second.getList().getFirst().getRoleName());
  query.setPageNo(3);assertTrue(staff.queryStaffPage(query).getList().isEmpty());
 }
 @Test void teacherNamePhoneAndStatusFiltersRemainEffectiveAndSingleTypeQueriesAreUnchanged(){
  var query=query();query.setName(prefix+"0");assertEquals(List.of(ids.getFirst()),resultIds(staff.queryStaffPage(query).getList()));
  query.setName(prefix);query.setPhone(teacherPhone);assertEquals(List.of(ids.getFirst()),resultIds(staff.queryStaffPage(query).getList()));
  query.setPhone(null);query.setStatus(1);assertEquals(3L,staff.queryStaffPage(query).getTotal());
  query.setPageSize(100);assertEquals(2L,details.queryUserDetailByPage(query,UserType.TEACHER).getTotal());
  assertEquals(List.of(ids.get(4)),details.queryUserDetailByPage(query,UserType.STUDENT).getRecords().stream().map(row->row.getId()).toList());
 }
}
