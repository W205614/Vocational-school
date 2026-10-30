package com.tianji.authsdk.gateway;

import com.tianji.authsdk.gateway.util.*;
import com.tianji.common.domain.R;
import com.tianji.common.domain.dto.LoginUserDTO;
import com.tianji.common.exceptions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.List;
import java.util.concurrent.*;
import static com.tianji.auth.common.constants.JwtConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_REDIS_PORT",matches="[0-9]+")
class PermissionSnapshotRedisTest {
 private LettuceConnectionFactory connection;private StringRedisTemplate redis;private AuthUtil auth;
 @BeforeEach void setup(){
  var config=new RedisStandaloneConfiguration("127.0.0.1",Integer.parseInt(System.getenv("ACCEPTANCE_REDIS_PORT")));config.setDatabase(15);
  connection=new LettuceConnectionFactory(config);connection.afterPropertiesSet();connection.start();redis=new StringRedisTemplate(connection);
  redis.delete(List.of(AUTH_PRIVILEGE_KEY,AUTH_PRIVILEGE_VERSION_KEY));
  auth=new AuthUtil(mock(JwtSignerHolder.class),redis,mock(SessionVerifier.class));
 }
 @AfterEach void cleanup(){if(redis!=null)redis.delete(List.of(AUTH_PRIVILEGE_KEY,AUTH_PRIVILEGE_VERSION_KEY));if(connection!=null)connection.destroy();}
 private R<LoginUserDTO> identity(long role){var user=new LoginUserDTO();user.setRoleId(role);return R.ok(user);}
 private void publish(){redis.opsForHash().put(AUTH_PRIVILEGE_KEY,"1","{\"id\":1,\"antPath\":\"GET:/restricted\",\"roles\":[1]}");redis.opsForValue().set(AUTH_PRIVILEGE_VERSION_KEY,"1");}
 @Test void missingPublicationNeverBecomesReadyOrAuthorizesDeclaredEndpoints(){
  assertThrows(ServiceUnavailableException.class,auth::refreshTask);assertFalse(auth.isReady());
  assertThrows(ServiceUnavailableException.class,()->auth.checkAuth("GET:/restricted",identity(2),true));
 }
 @Test void publishedSnapshotStillEnforcesDynamicRoles(){
  publish();auth.refreshTask();assertTrue(auth.isReady());
  assertThrows(ForbiddenException.class,()->auth.checkAuth("GET:/restricted",identity(2),true));
  assertDoesNotThrow(()->auth.checkAuth("GET:/restricted",identity(1),true));
 }
 @Test void unchangedVersionRefreshKeepsReadinessAndRoleRestrictions(){
  publish();auth.refreshTask();auth.refreshTask();assertTrue(auth.isReady());
  assertThrows(ForbiddenException.class,()->auth.checkAuth("GET:/restricted",identity(2),true));
  assertDoesNotThrow(()->auth.checkAuth("GET:/restricted",identity(1),true));
 }
 @Test void publicationDuringReadinessCannotAuthorizeAnUnreadyCapturedSnapshot()throws Exception{
  var readinessEntered=new CompletableFuture<Void>();var published=new CompletableFuture<Void>();
  auth=new AuthUtil(mock(JwtSignerHolder.class),redis,mock(SessionVerifier.class)){
   @Override public boolean isReady(){
    // Pause the former authorization callback until the real snapshot publication completes.
    readinessEntered.complete(null);published.orTimeout(5,TimeUnit.SECONDS).join();return super.isReady();
   }
  };
  try(var requests=Executors.newSingleThreadExecutor()){
   var decision=CompletableFuture.supplyAsync(()->{
    try{auth.checkAuth("GET:/restricted",identity(2),true);return null;}
    catch(CommonException denied){return denied;}
   },requests);
   try{
    CompletableFuture.anyOf(readinessEntered,decision).get(5,TimeUnit.SECONDS);
    publish();auth.refreshTask();
   }finally{published.complete(null);}
   assertInstanceOf(ServiceUnavailableException.class,decision.get(5,TimeUnit.SECONDS));
   assertThrows(ForbiddenException.class,()->auth.checkAuth("GET:/restricted",identity(2),true));
  }
 }
 @Test void lostVersionInvalidatesAnExistingSnapshotImmediately(){
  publish();auth.refreshTask();redis.delete(List.of(AUTH_PRIVILEGE_KEY,AUTH_PRIVILEGE_VERSION_KEY));
  assertThrows(ServiceUnavailableException.class,auth::refreshTask);assertFalse(auth.isReady());
  assertThrows(ServiceUnavailableException.class,()->auth.checkAuth("GET:/declared",identity(2),true));
 }
 @Test void explicitlyPublishedEmptySnapshotKeepsTheDeclaredEndpointContract(){
  redis.opsForValue().set(AUTH_PRIVILEGE_VERSION_KEY,"1");auth.refreshTask();assertTrue(auth.isReady());
  assertDoesNotThrow(()->auth.checkAuth("GET:/declared",identity(2),true));
  assertThrows(ForbiddenException.class,()->auth.checkAuth("GET:/unknown",identity(2),false));
 }
}
