package com.tianji.auth.security;

import com.tianji.auth.common.domain.PrivilegeRoleDTO;
import com.tianji.auth.task.*;
import com.tianji.auth.util.PrivilegeCache;
import com.tianji.common.autoconfigure.reliability.OutboxStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static com.tianji.auth.common.constants.JwtConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_REDIS_PORT",matches="[0-9]+")
class PermissionPublicationTest {
 private LettuceConnectionFactory connection;private StringRedisTemplate redis;private PrivilegeCache cache;private OutboxStore outbox;
 @BeforeEach void setup(){
  var config=new RedisStandaloneConfiguration("127.0.0.1",Integer.parseInt(System.getenv("ACCEPTANCE_REDIS_PORT")));config.setDatabase(14);
  connection=new LettuceConnectionFactory(config);connection.afterPropertiesSet();connection.start();redis=new StringRedisTemplate(connection);
  redis.delete(List.of(AUTH_PRIVILEGE_KEY,AUTH_PRIVILEGE_VERSION_KEY));outbox=mock(OutboxStore.class);cache=new PrivilegeCache(redis,outbox,mock(JdbcTemplate.class));
 }
 @AfterEach void cleanup(){if(redis!=null)redis.delete(List.of(AUTH_PRIVILEGE_KEY,AUTH_PRIVILEGE_VERSION_KEY));if(connection!=null)connection.destroy();}
 @Test void fullPublicationReplacesOldRulesAndIncrementsVersion(){
  var rule=new PrivilegeRoleDTO();rule.setId(1L);rule.setAntPath("GET:/restricted");rule.setRoles(Set.of(1L));
  cache.publishCommittedSnapshot(List.of(rule));assertEquals("1",redis.opsForValue().get(AUTH_PRIVILEGE_VERSION_KEY));assertEquals(1,redis.opsForHash().size(AUTH_PRIVILEGE_KEY));
  rule.setId(2L);cache.publishCommittedSnapshot(List.of(rule));assertEquals("2",redis.opsForValue().get(AUTH_PRIVILEGE_VERSION_KEY));assertFalse(redis.opsForHash().hasKey(AUTH_PRIVILEGE_KEY,"1"));assertTrue(redis.opsForHash().hasKey(AUTH_PRIVILEGE_KEY,"2"));
  cache.publishCommittedSnapshot(List.of());assertEquals("3",redis.opsForValue().get(AUTH_PRIVILEGE_VERSION_KEY));assertEquals(0,redis.opsForHash().size(AUTH_PRIVILEGE_KEY));
 }
 @Test void permissionChangeCannotPublishOutsideATransaction(){assertThrows(IllegalStateException.class,cache::publishChange);verifyNoInteractions(outbox);}
 @Test void initialRebuildFailurePropagates(){
  var publication=mock(PermissionPublication.class);var failure=new IllegalStateException("fixture redis unavailable");doThrow(failure).when(publication).rebuild();
  assertSame(failure,assertThrows(IllegalStateException.class,()->new LoadPrivilegeRunner(publication).loadPrivilegeCache()));
 }
}
