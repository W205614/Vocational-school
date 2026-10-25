package com.tianji.gateway.config;

import com.tianji.common.exceptions.TooManyRequestsException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_REDIS_PORT",matches="[0-9]+")
class GatewayQuotaRedisTest {
 LettuceConnectionFactory factory;StringRedisTemplate redis;long user;
 @BeforeEach void connect(){
  factory=new LettuceConnectionFactory("127.0.0.1",Integer.parseInt(System.getenv("ACCEPTANCE_REDIS_PORT")));factory.afterPropertiesSet();
  redis=new StringRedisTemplate(factory);redis.afterPropertiesSet();user=java.util.concurrent.ThreadLocalRandom.current().nextLong(1,Long.MAX_VALUE);
 }
 @AfterEach void close(){redis.delete("gateway:quota:user:"+user);factory.destroy();}
 @Test void sharedQuotaSurvivesLimiterRecreationAndHasBoundedLifetime(){
  var limits=new GatewayTrafficLimits(redis,1,1,1,1,2,120,30);limits.authenticated(user);limits.authenticated(user);
  assertThrows(TooManyRequestsException.class,()->limits.authenticated(user));
  var restarted=new GatewayTrafficLimits(redis,1,1,1,1,2,120,30);assertThrows(TooManyRequestsException.class,()->restarted.authenticated(user));
  Long ttl=redis.getExpire("gateway:quota:user:"+user);assertNotNull(ttl);assertTrue(ttl>0 && ttl<=180);
 }
 @Test void concurrentRequestsCannotOverspendOneIdentityBurst()throws Exception{
  var limits=new GatewayTrafficLimits(redis,1,1,1,1,3,120,30);var accepted=new AtomicInteger();var rejected=new AtomicInteger();
  try(var pool=Executors.newFixedThreadPool(8)){
   var tasks=new java.util.ArrayList<Future<?>>();for(int n=0;n<24;n++)tasks.add(pool.submit(()->{try{limits.authenticated(user);accepted.incrementAndGet();}catch(TooManyRequestsException error){rejected.incrementAndGet();}}));
   for(var task:tasks)task.get(5,TimeUnit.SECONDS);
  }
  assertEquals(3,accepted.get());assertEquals(21,rejected.get());
 }
}
