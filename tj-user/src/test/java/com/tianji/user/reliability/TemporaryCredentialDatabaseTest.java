package com.tianji.user.reliability;
import com.tianji.user.service.impl.TemporaryCredentialService;
import com.tianji.api.dto.user.*;
import com.tianji.common.exceptions.BadRequestException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class TemporaryCredentialDatabaseTest {
 private JdbcTemplate jdbc;private TemporaryCredentialService service;private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(4);private long id;private String username;
 @BeforeEach void setup(){
  String base="jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/";
  String options="?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true";
  new JdbcTemplate(new DriverManagerDataSource(base+options,"root",System.getenv("ACCEPTANCE_DB_PASSWORD"))).execute("CREATE DATABASE IF NOT EXISTS acceptance_initial_password");
  var source=new DriverManagerDataSource(base+"acceptance_initial_password"+options,"root",System.getenv("ACCEPTANCE_DB_PASSWORD"));jdbc=new JdbcTemplate(source);
  assertEquals("acceptance_initial_password",jdbc.queryForObject("SELECT DATABASE()",String.class));
  jdbc.execute("CREATE TABLE IF NOT EXISTS `user`(id BIGINT PRIMARY KEY,username VARCHAR(100) UNIQUE,password VARCHAR(100) NOT NULL,type INT NOT NULL,status INT NOT NULL DEFAULT 1,auth_version BIGINT NOT NULL DEFAULT 0,must_change_password TINYINT NOT NULL DEFAULT 0,temporary_password_expires_at DATETIME(3)) ENGINE=InnoDB");
  jdbc.execute("CREATE TABLE IF NOT EXISTS user_password_upgrade_guard(id INT PRIMARY KEY,completed TINYINT NOT NULL DEFAULT 0) ENGINE=InnoDB");
  jdbc.update("INSERT IGNORE INTO user_password_upgrade_guard(id,completed) VALUES(1,0)");
  if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name='credential_version'",Integer.class)==0)
   jdbc.execute("CREATE TRIGGER credential_version BEFORE UPDATE ON `user` FOR EACH ROW SET NEW.auth_version=IF(NOT(OLD.password <=> NEW.password),OLD.auth_version+1,NEW.auth_version)");
  service=new TemporaryCredentialService(jdbc,passwords,new DataSourceTransactionManager(source));id=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();username="credential-"+id;
  jdbc.update("INSERT INTO `user`(id,username,password,type) VALUES(?,?,?,3)",id,username,passwords.encode("custom-password-fixture"));
 }
 @AfterEach void cleanup(){if(jdbc!=null)jdbc.update("DELETE FROM `user` WHERE username LIKE 'credential-%'");}
 private InitialPasswordDTO form(TemporaryCredentialDTO receipt,String next,String audience){return new InitialPasswordDTO(username,receipt.temporaryPassword(),next,audience);}
 @Test void resetIsRandomExpiresIn24HoursAndActivationIsOneTime(){
  var first=service.reset(id);var receipt=service.reset(id);assertNotEquals(first.temporaryPassword(),receipt.temporaryPassword());
  assertTrue(receipt.temporaryPassword().length()>=24);assertTrue(jdbc.queryForObject("SELECT temporary_password_expires_at BETWEEN NOW()+INTERVAL 23 HOUR AND NOW()+INTERVAL 25 HOUR FROM `user` WHERE id=?",Boolean.class,id));
  assertEquals(2L,jdbc.queryForObject("SELECT auth_version FROM `user` WHERE id=?",Long.class,id));
  assertThrows(BadRequestException.class,()->service.complete(form(first,"new-password-fixture","admin")));
  service.complete(form(receipt,"new-password-fixture","admin"));
  assertFalse(jdbc.queryForObject("SELECT must_change_password FROM `user` WHERE id=?",Boolean.class,id));assertTrue(passwords.matches("new-password-fixture",jdbc.queryForObject("SELECT password FROM `user` WHERE id=?",String.class,id)));
  assertEquals(3L,jdbc.queryForObject("SELECT auth_version FROM `user` WHERE id=?",Long.class,id));assertThrows(BadRequestException.class,()->service.complete(form(receipt,"another-password-fixture","admin")));
 }
 @Test void expiredWrongAudienceAndUnchangedPasswordAreRejected(){
  var receipt=service.reset(id);assertThrows(BadRequestException.class,()->service.complete(form(receipt,"new-password-fixture","student")));
  assertThrows(BadRequestException.class,()->service.complete(form(receipt,receipt.temporaryPassword(),"admin")));
  jdbc.update("UPDATE `user` SET temporary_password_expires_at=NOW()-INTERVAL 1 SECOND WHERE id=?",id);
  assertThrows(BadRequestException.class,()->service.complete(form(receipt,"new-password-fixture","admin")));
 }
 @Test void legacyDefaultUpgradeInvalidatesOnlyKnownDefaultAndIsIdempotent(){
  long legacy=id+1;jdbc.update("INSERT INTO `user`(id,username,password,type) VALUES(?,?,?,2)",legacy,"credential-"+legacy,passwords.encode(com.tianji.user.constants.UserConstants.DEFAULT_PASSWORD));
  String original=jdbc.queryForObject("SELECT password FROM `user` WHERE id=?",String.class,id);jdbc.update("UPDATE user_password_upgrade_guard SET completed=0 WHERE id=1");
  service.run(null);assertEquals(original,jdbc.queryForObject("SELECT password FROM `user` WHERE id=?",String.class,id));
  assertTrue(jdbc.queryForObject("SELECT must_change_password=1 AND temporary_password_expires_at IS NULL FROM `user` WHERE id=?",Boolean.class,legacy));
  String disabled=jdbc.queryForObject("SELECT password FROM `user` WHERE id=?",String.class,legacy);assertFalse(passwords.matches(com.tianji.user.constants.UserConstants.DEFAULT_PASSWORD,disabled));
  service.run(null);assertEquals(disabled,jdbc.queryForObject("SELECT password FROM `user` WHERE id=?",String.class,legacy));
 }
}
