package com.tianji.auth.security;
import com.tianji.auth.service.impl.SessionStore;
import com.tianji.common.domain.dto.LoginUserDTO;
import com.tianji.common.exceptions.UnauthorizedException;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.*;import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;import java.util.*;import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_AUTH_DB_URL",matches=".+/acceptance_auth.*")
class SessionStoreTest {
 private JdbcTemplate jdbc;private SessionStore store;private LoginUserDTO identity;
 @BeforeEach void setup(){var ds=new DriverManagerDataSource(System.getenv("ACCEPTANCE_AUTH_DB_URL"),"root",System.getenv("ACCEPTANCE_DB_PASSWORD"));jdbc=new JdbcTemplate(ds);store=new SessionStore(jdbc,new TransactionTemplate(new DataSourceTransactionManager(ds)));identity=new LoginUserDTO();identity.setUserId(Math.abs(UUID.randomUUID().getMostSignificantBits())/2+1);identity.setRoleId(2L);identity.setAuthVersion(0L);}
 private LoginUserDTO copy(String jti){var i=new LoginUserDTO();i.setUserId(identity.getUserId());i.setRoleId(identity.getRoleId());i.setAuthVersion(identity.getAuthVersion());i.setSessionId(identity.getSessionId());i.setRefreshJti(jti);return i;}
 @AfterEach void cleanup(){if(identity!=null)jdbc.update("DELETE FROM auth_session WHERE user_id=?",identity.getUserId());}
 @Test void concurrentRefreshAndLostResponseRecoverOneRotation()throws Exception{
  var initial=store.rotate(identity,Duration.ofMinutes(30));Set<String> outputs=ConcurrentHashMap.newKeySet();try(var pool=Executors.newFixedThreadPool(8)){List<Future<?>> work=new ArrayList<>();for(int n=0;n<20;n++)work.add(pool.submit(()->outputs.add(store.rotate(copy(initial.jti()),Duration.ofMinutes(30)).jti())));for(var task:work)task.get(20,TimeUnit.SECONDS);}assertEquals(1,outputs.size());
  jdbc.update("UPDATE auth_session SET previous_until=NOW()-INTERVAL 1 SECOND WHERE session_id=?",identity.getSessionId());assertThrows(UnauthorizedException.class,()->store.rotate(copy(initial.jti()),Duration.ofMinutes(30)));
 }
 @Test void revokedAndChangedIdentityCannotRefreshOrValidate(){var rotation=store.rotate(identity,Duration.ofMinutes(30));store.validate(identity);var changed=copy(rotation.jti());changed.setRoleId(1L);assertThrows(UnauthorizedException.class,()->store.rotate(changed,Duration.ofMinutes(30)));store.revoke(identity.getSessionId(),identity.getUserId()+1);store.validate(identity);store.revoke(identity.getSessionId(),identity.getUserId());assertThrows(UnauthorizedException.class,()->store.validate(identity));assertThrows(UnauthorizedException.class,()->store.rotate(copy(rotation.jti()),Duration.ofMinutes(30)));}
 @Test void separateLoginsAreIndependentlyRevocable(){store.rotate(identity,Duration.ofMinutes(30));var second=copy(null);second.setSessionId(null);store.rotate(second,Duration.ofMinutes(30));store.revoke(identity.getSessionId(),identity.getUserId());store.validate(second);assertThrows(UnauthorizedException.class,()->store.validate(identity));store.revokeAll(identity.getUserId());assertThrows(UnauthorizedException.class,()->store.validate(second));}
}
