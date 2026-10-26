package com.tianji.user.reliability;
import com.tianji.user.service.impl.*;
import com.tianji.common.utils.UserContext;
import com.tianji.common.exceptions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class PublicAccountDatabaseTest {
    private JdbcTemplate jdbc;private PublicAccountService accounts;private AccountPrivacyService privacy;private String phone;private long user;private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(4);
    @BeforeEach void setup()throws Exception{
        String base="jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/",options="?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true";
        new JdbcTemplate(new DriverManagerDataSource(base+options,"root",System.getenv("ACCEPTANCE_DB_PASSWORD"))).execute("CREATE DATABASE IF NOT EXISTS acceptance_public_accounts");
        var source=new DriverManagerDataSource(base+"acceptance_public_accounts"+options,"root",System.getenv("ACCEPTANCE_DB_PASSWORD"));jdbc=new JdbcTemplate(source);assertEquals("acceptance_public_accounts",jdbc.queryForObject("SELECT DATABASE()",String.class));
        Path root=Path.of("").toAbsolutePath();while(!Files.isDirectory(root.resolve("deploy/acceptance/migrations")))root=root.getParent();String schema=Files.readString(root.resolve("deploy/compact/schema.sql"));
        for(String table:List.of("user","user_detail")){var matcher=Pattern.compile("CREATE TABLE `"+table+"` \\(.*?\\) ENGINE=.*?;",Pattern.DOTALL).matcher(schema);assertTrue(matcher.find());jdbc.execute(matcher.group().replace("CREATE TABLE","CREATE TABLE IF NOT EXISTS"));}
        if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='user' AND column_name='auth_version'",Integer.class)==0){jdbc.execute("ALTER TABLE `user` ADD auth_version BIGINT NOT NULL DEFAULT 0,ADD must_change_password TINYINT NOT NULL DEFAULT 0,ADD temporary_password_expires_at DATETIME(3)");jdbc.execute("CREATE TRIGGER public_account_auth_version BEFORE UPDATE ON `user` FOR EACH ROW SET NEW.auth_version=IF(NOT(OLD.password<=>NEW.password) OR NOT(OLD.status<=>NEW.status),OLD.auth_version+1,NEW.auth_version)");}
        for(String statement:Files.readString(root.resolve("deploy/acceptance/migrations/tj_user/V004__public_account_privacy.sql")).split(";"))if(!statement.isBlank())jdbc.execute(statement.replace("CREATE TABLE","CREATE TABLE IF NOT EXISTS"));
        var proof=mock(AccountVerificationService.class);var env=new MockEnvironment().withProperty("tj.accounts.public.enabled","true").withProperty("tj.accounts.public.sms-delivery-mode","provider");var tx=new TransactionTemplate(new DataSourceTransactionManager(source));accounts=new PublicAccountService(jdbc,tx,passwords,proof,env);privacy=new AccountPrivacyService(jdbc,tx,passwords,accounts,env);
        phone="139"+String.format(Locale.ROOT,"%08d",Math.floorMod(UUID.randomUUID().getMostSignificantBits(),100_000_000L));accounts.register(new PublicAccountService.Registration(phone,"123456","fixture-password","2026-10-26",true));user=jdbc.queryForObject("SELECT id FROM `user` WHERE username=?",Long.class,phone);UserContext.setUser(user);UserContext.setRole(2L);
    }
    @AfterEach void cleanup(){UserContext.removeUser();if(jdbc!=null&&user!=0){jdbc.update("DELETE FROM user_privacy_consent WHERE user_id=?",user);jdbc.update("DELETE FROM user_privacy_request WHERE user_id=?",user);jdbc.update("DELETE FROM user_deletion_request WHERE user_id=?",user);jdbc.update("DELETE FROM user_detail WHERE id=?",user);jdbc.update("DELETE FROM `user` WHERE id=?",user);}}
    @Test void registrationPersistsConsentAndPasswordHash(){assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_privacy_consent WHERE user_id=? AND policy_version='2026-10-26'",Integer.class,user));String hash=jdbc.queryForObject("SELECT password FROM `user` WHERE id=?",String.class,user);assertNotEquals("fixture-password",hash);assertTrue(passwords.matches("fixture-password",hash));}
    @Test void recoveryInvalidatesOldAuthVersionWithoutReactivatingDisabledAccounts(){accounts.recover(new PublicAccountService.Recovery(phone,"123456","fixture-next-password"));assertEquals(1L,jdbc.queryForObject("SELECT auth_version FROM `user` WHERE id=?",Long.class,user));jdbc.update("UPDATE `user` SET status=0 WHERE id=?",user);accounts.recover(new PublicAccountService.Recovery(phone,"123456","fixture-forbidden-password"));assertFalse(passwords.matches("fixture-forbidden-password",jdbc.queryForObject("SELECT password FROM `user` WHERE id=?",String.class,user)));}
    @Test void ownExportOmitsCredentialAndDeclaresItsScope(){var data=privacy.export("fixture-password");assertEquals("ACCOUNT_PROFILE_AND_CONSENT",data.get("scope"));assertFalse(((Map<?,?>)data.get("profile")).containsKey("password"));assertThrows(UnauthorizedException.class,()->privacy.export("wrong-password"));}
    @Test void deactivationRequiresPasswordThenPreservesRetentionRecord(){assertThrows(UnauthorizedException.class,()->privacy.deactivate("wrong-password","注销账户"));privacy.deactivate("fixture-password","注销账户");assertEquals(0,jdbc.queryForObject("SELECT status FROM `user` WHERE id=?",Integer.class,user));assertEquals("DEACTIVATED_RETAINED",jdbc.queryForObject("SELECT status FROM user_deletion_request WHERE user_id=?",String.class,user));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_detail WHERE id=? AND name='已注销学员'",Integer.class,user));accounts.recover(new PublicAccountService.Recovery(phone,"123456","fixture-recovered-password"));assertEquals(0,jdbc.queryForObject("SELECT status FROM `user` WHERE id=?",Integer.class,user));}
    @Test void completeExportIsPendingAndOperatorResolutionRequiresEvidenceAndCurrentVersion(){
        var exported=privacy.export("fixture-password");var request=(Map<?,?>)exported.get("request");assertEquals("ALL_DOMAINS",request.get("scope"));assertEquals("PENDING_REVIEW",request.get("status"));String id=request.get("id").toString();
        assertThrows(ForbiddenException.class,()->privacy.pending());UserContext.setRole(1L);
        assertThrows(BadRequestException.class,()->privacy.resolve(id,new AccountPrivacyService.Resolution(0,"COMPLETED","fixture policy",null)));
        privacy.resolve(id,new AccountPrivacyService.Resolution(0,"IN_PROGRESS","fixture review",null));
        assertThrows(ConflictException.class,()->privacy.resolve(id,new AccountPrivacyService.Resolution(0,"COMPLETED","fixture delivered","fixture-ticket")));
        privacy.resolve(id,new AccountPrivacyService.Resolution(1,"COMPLETED","fixture delivered","fixture-ticket"));assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM user_privacy_request WHERE id=?",String.class,id));
        UserContext.setRole(2L);var next=(Map<?,?>)privacy.export("fixture-password").get("request");assertNotEquals(id,next.get("id"));assertEquals("PENDING_REVIEW",next.get("status"));
    }
}
