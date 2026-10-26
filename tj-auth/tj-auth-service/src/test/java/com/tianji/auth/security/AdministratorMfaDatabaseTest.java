package com.tianji.auth.security;
import com.tianji.auth.service.impl.*;
import com.tianji.common.domain.dto.LoginUserDTO;
import com.tianji.common.exceptions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class AdministratorMfaDatabaseTest {
    private JdbcTemplate jdbc;private TransactionTemplate tx;private SessionStore sessions;private AdministratorMfaService first;private long user;private final String key=Base64.getEncoder().encodeToString(new byte[32]);
    @BeforeEach void setup()throws Exception{
        String base="jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/",options="?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true";
        new JdbcTemplate(new DriverManagerDataSource(base+options,"root",System.getenv("ACCEPTANCE_DB_PASSWORD"))).execute("CREATE DATABASE IF NOT EXISTS acceptance_administrator_mfa");
        var source=new DriverManagerDataSource(base+"acceptance_administrator_mfa"+options,"root",System.getenv("ACCEPTANCE_DB_PASSWORD"));jdbc=new JdbcTemplate(source);assertEquals("acceptance_administrator_mfa",jdbc.queryForObject("SELECT DATABASE()",String.class));
        Path root=Path.of("").toAbsolutePath();while(!Files.isDirectory(root.resolve("deploy/acceptance/migrations")))root=root.getParent();
        for(String statement:Files.readString(root.resolve("deploy/acceptance/migrations/tj_auth/V003__sessions.sql")).split(";"))if(!statement.isBlank())jdbc.execute(statement.replace("CREATE TABLE","CREATE TABLE IF NOT EXISTS"));
        for(String statement:Files.readString(root.resolve("deploy/acceptance/migrations/tj_auth/V005__administrator_mfa.sql")).split(";"))if(!statement.isBlank()){
            if(statement.contains("ALTER TABLE") && jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='auth_session' AND column_name='mfa_verified'",Integer.class)>0)continue;
            jdbc.execute(statement.replace("CREATE TABLE","CREATE TABLE IF NOT EXISTS"));
        }
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));sessions=new SessionStore(jdbc,tx);first=service();user=Math.abs(UUID.randomUUID().getMostSignificantBits())/2+1;
    }
    private AdministratorMfaService service(){return new AdministratorMfaService(jdbc,tx,sessions,new MockEnvironment().withProperty("tj.auth.mfa.required","true").withProperty("TJ_MFA_ENCRYPTION_KEY",key));}
    private LoginUserDTO identity(){var value=new LoginUserDTO();value.setUserId(user);value.setRoleId(1L);value.setAuthVersion(0L);return value;}
    private long step(){return jdbc.queryForObject("SELECT FLOOR(UNIX_TIMESTAMP(NOW())/30)",Long.class);}
    @AfterEach void cleanup(){if(jdbc!=null){jdbc.update("DELETE FROM auth_mfa WHERE user_id=?",user);jdbc.update("DELETE FROM auth_session WHERE user_id=?",user);}}
    @Test void expiredEnrollmentCannotActivateAndConfirmedSecretsCannotBeReissued(){
        var expired=first.enroll(user);jdbc.update("UPDATE auth_mfa SET pending_until=NOW()-INTERVAL 1 SECOND WHERE user_id=?",user);
        assertThrows(BadRequestException.class,()->first.confirm(user,TotpSecrets.code(expired.secret(),step(),6)));
        var enrollment=first.enroll(user);first.confirm(user,TotpSecrets.code(enrollment.secret(),step(),6));assertThrows(ForbiddenException.class,()->first.enroll(user));
        assertFalse(jdbc.queryForObject("SELECT secret_ciphertext FROM auth_mfa WHERE user_id=?",String.class,user).contains(enrollment.secret()));
    }
    @Test void twoReplicasAcceptAOneTimeCodeExactlyOnce()throws Exception{
        var enrollment=first.enroll(user);first.confirm(user,TotpSecrets.code(enrollment.secret(),step(),6));String next=TotpSecrets.code(enrollment.secret(),step()+1,6);var second=service();var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            List<Future<Boolean>> results=new ArrayList<>();for(var instance:List.of(first,second))results.add(pool.submit(()->{gate.await();try{return instance.verifyLogin(identity(),next);}catch(UnauthorizedException rejected){return false;}}));gate.countDown();int accepted=0;for(var result:results)if(result.get(10,TimeUnit.SECONDS))accepted++;assertEquals(1,accepted);
        }
    }
    @Test void oldNonMfaSessionCannotValidateOrRefreshWhenEnforcementTurnsOn(){
        ReflectionTestUtils.setField(sessions,"requireMfa",true);var userIdentity=identity();var token=sessions.rotate(userIdentity,Duration.ofMinutes(10));
        assertThrows(UnauthorizedException.class,()->sessions.validate(userIdentity));userIdentity.setRefreshJti(token.jti());assertThrows(UnauthorizedException.class,()->sessions.rotate(userIdentity,Duration.ofMinutes(10)));
        first.markSession(userIdentity);sessions.validate(userIdentity);sessions.rotate(userIdentity,Duration.ofMinutes(10));
    }
}
