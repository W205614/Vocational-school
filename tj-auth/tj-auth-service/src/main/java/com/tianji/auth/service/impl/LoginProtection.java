package com.tianji.auth.service.impl;
import org.springframework.stereotype.Service;import org.springframework.data.redis.core.StringRedisTemplate;import org.springframework.data.redis.core.script.DefaultRedisScript;import org.springframework.jdbc.core.JdbcTemplate;
import com.tianji.common.exceptions.TooManyRequestsException;import java.util.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
@Service
public class LoginProtection {
 private final StringRedisTemplate redis;private final JdbcTemplate jdbc;
 private static final DefaultRedisScript<Long> INCREMENT=new DefaultRedisScript<>("local n=redis.call('INCR',KEYS[1]);if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end;return n",Long.class);
 public LoginProtection(StringRedisTemplate redis,JdbcTemplate jdbc){this.redis=redis;this.jdbc=jdbc;}
 private static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 public void before(String account,String ip){
  if(redis.execute(INCREMENT,List.of("auth:login:ip:"+digest(ip)),"60")>60)throw new TooManyRequestsException("登录请求过于频繁，请稍后重试");
  if(Boolean.TRUE.equals(redis.hasKey("auth:login:lock:"+digest(account))))throw new TooManyRequestsException("登录失败次数过多，请稍后重试");
 }
 public void failed(String account,String ip){
  Long failures=redis.execute(INCREMENT,List.of("auth:login:fail:"+digest(account)),"900");if(failures!=null && failures>=10)redis.opsForValue().set("auth:login:lock:"+digest(account),"1",java.time.Duration.ofSeconds(30));
  jdbc.update("INSERT INTO auth_login_failure(account_hash,ip_hash) VALUES(?,?)",digest(account),digest(ip));
 }
 @org.springframework.scheduling.annotation.Scheduled(fixedDelay=3600000) public void retainFailures(){jdbc.update("DELETE FROM auth_login_failure WHERE created_at<NOW()-INTERVAL 30 DAY LIMIT 1000");jdbc.update("DELETE FROM auth_session WHERE expires_at<NOW()-INTERVAL 30 DAY LIMIT 1000");}
 public void success(String account){redis.delete(List.of("auth:login:fail:"+digest(account),"auth:login:lock:"+digest(account)));}
}
