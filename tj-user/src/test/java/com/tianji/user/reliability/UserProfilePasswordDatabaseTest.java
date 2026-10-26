package com.tianji.user.reliability;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.common.exceptions.UnauthorizedException;
import com.tianji.common.utils.UserContext;
import com.tianji.user.domain.dto.UserFormDTO;
import com.tianji.user.mapper.UserDetailMapper;
import com.tianji.user.mapper.UserMapper;
import com.tianji.user.service.impl.UserDetailServiceImpl;
import com.tianji.user.service.impl.UserServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Real mapper regression for the password-only profile request used after initial setup. */
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class UserProfilePasswordDatabaseTest {
 @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
 @org.springframework.boot.autoconfigure.ImportAutoConfiguration({
  com.tianji.common.autoconfigure.mybatis.MybatisConfig.class,
  com.baomidou.mybatisplus.autoconfigure.MybatisPlusInnerInterceptorAutoConfiguration.class,
  com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class})
 static class FactoryConfiguration {}
 private JdbcTemplate jdbc;private UserServiceImpl users;private TransactionTemplate transactions;
 private org.springframework.context.ConfigurableApplicationContext context;
 private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(4);
 private final List<Long> ids=new ArrayList<>();
 @BeforeEach void setup()throws Exception {
  String base="jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/";
  String options="?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true";
  new JdbcTemplate(new DriverManagerDataSource(base+options,"root",System.getenv("ACCEPTANCE_DB_PASSWORD"))).execute("CREATE DATABASE IF NOT EXISTS acceptance_profile_password");
  var source=new DriverManagerDataSource(base+"acceptance_profile_password"+options,"root",System.getenv("ACCEPTANCE_DB_PASSWORD"));jdbc=new JdbcTemplate(source);
  assertEquals("acceptance_profile_password",jdbc.queryForObject("SELECT DATABASE()",String.class));
  Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
  while(root!=null&&!Files.isRegularFile(root.resolve("deploy/compact/schema.sql")))root=root.getParent();assertNotNull(root);
  String schema=Files.readString(root.resolve("deploy/compact/schema.sql"));
  for(String table:List.of("user","user_detail")){
   var definition=Pattern.compile("CREATE TABLE `"+table+"` \\(.*?\\) ENGINE=.*?;",Pattern.DOTALL).matcher(schema);
   assertTrue(definition.find());jdbc.execute(definition.group().replaceFirst("CREATE TABLE","CREATE TABLE IF NOT EXISTS"));
  }
  for(var field:Map.of("must_change_password","TINYINT NOT NULL DEFAULT 0","temporary_password_expires_at","DATETIME(3) NULL").entrySet())
   if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='user' AND column_name=?",Integer.class,field.getKey())==0)
    jdbc.execute("ALTER TABLE `user` ADD COLUMN "+field.getKey()+" "+field.getValue());
  for(int n=0;n<2;n++){
   long id=IdWorker.getId();ids.add(id);
   jdbc.update("INSERT INTO `user`(id,username,cell_phone,password,type,status) VALUES(?,?,?,?,2,1)",id,"profile-"+id,String.format("131%08d",Math.floorMod(id,100000000L)),passwords.encode("original-profile-password"));
   jdbc.update("INSERT INTO user_detail(id,type,name,email,role_id) VALUES(?,2,?,?,2)",id,"Original profile "+n,"profile"+n+"@example.test");
  }
  var application=new org.springframework.context.annotation.AnnotationConfigApplicationContext();
  application.registerBean("dataSource",javax.sql.DataSource.class,()->source);
  application.register(FactoryConfiguration.class);application.refresh();context=application;
  var factory=context.getBean(SqlSessionFactory.class);
  for(var mapper:List.of(UserMapper.class,UserDetailMapper.class))if(!factory.getConfiguration().hasMapper(mapper))factory.getConfiguration().addMapper(mapper);
  var sessions=new SqlSessionTemplate(factory);var details=new UserDetailServiceImpl();ReflectionTestUtils.setField(details,"baseMapper",sessions.getMapper(UserDetailMapper.class));
  users=new UserServiceImpl();ReflectionTestUtils.setField(users,"baseMapper",sessions.getMapper(UserMapper.class));
  ReflectionTestUtils.setField(users,"detailService",details);ReflectionTestUtils.setField(users,"passwordEncoder",passwords);
  transactions=new TransactionTemplate(new DataSourceTransactionManager(source));UserContext.setUser(ids.getFirst());
 }
 @AfterEach void cleanup(){
  UserContext.removeUser();if(context!=null)context.close();if(jdbc!=null)for(long id:ids){jdbc.update("DELETE FROM user_detail WHERE id=?",id);jdbc.update("DELETE FROM `user` WHERE id=?",id);}
 }
 private Map<String,Object> detail(long id){return jdbc.queryForMap("SELECT * FROM user_detail WHERE id=?",id);}
 private String hash(){return jdbc.queryForObject("SELECT password FROM `user` WHERE id=?",String.class,ids.getFirst());}
 private UserFormDTO form(){var form=new UserFormDTO();form.setType(2);form.setOldPassword("original-profile-password");form.setPassword("changed-profile-password");return form;}
 private void update(UserFormDTO form){transactions.executeWithoutResult(status->users.updateUserWithPassword(form));}
 @Test void passwordOnlyChangesHashWithoutWritingOrClearingProfile(){
  var before=detail(ids.getFirst());String originalHash=hash();var invalid=form();invalid.setOldPassword("incorrect-profile-password");
  assertThrows(UnauthorizedException.class,()->update(invalid));assertEquals(originalHash,hash());assertEquals(before,detail(ids.getFirst()));
  update(form());assertTrue(passwords.matches("changed-profile-password",hash()));assertNotEquals(originalHash,hash());assertEquals(before,detail(ids.getFirst()));
 }
 @Test void combinedPasswordAndProfileUpdateKeepsUnspecifiedFieldsAndCannotChangeIdentityOrRole(){
  var other=detail(ids.getLast());var form=form();form.setId(ids.getLast());form.setType(1);form.setRoleId(1L);form.setName("Updated own profile");
  update(form());var changed=detail(ids.getFirst());assertEquals("Updated own profile",changed.get("name"));assertEquals("profile0@example.test",changed.get("email"));
  assertEquals(2,((Number)changed.get("type")).intValue());assertEquals(2L,((Number)changed.get("role_id")).longValue());
  assertEquals(other,detail(ids.getLast()));assertTrue(passwords.matches("changed-profile-password",hash()));
 }
}
