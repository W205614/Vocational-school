package com.tianji.compact;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.boot.health.contributor.*;
import org.springframework.context.annotation.Bean;
import java.util.*;
import jakarta.annotation.PreDestroy;
public final class ModuleRegistry {
 public record Module(GenericWebApplicationContext context,DispatcherServlet servlet){}
 private final Map<String,Module> modules=new java.util.concurrent.ConcurrentHashMap<>();
 private final Map<String,org.redisson.api.RedissonClient> lockClients=new java.util.concurrent.ConcurrentHashMap<>();
 public org.redisson.api.RedissonClient locks(org.springframework.core.env.Environment env){
  String host=env.getRequiredProperty("spring.data.redis.host");int port=env.getProperty("spring.data.redis.port",Integer.class,6379),db=env.getProperty("spring.data.redis.database",Integer.class,0);String password=env.getProperty("spring.data.redis.password");
  String key=host+":"+port+":"+db+":"+java.util.Objects.toString(password,"");
  return lockClients.computeIfAbsent(key,k->{var config=new org.redisson.config.Config();config.setThreads(2);config.setNettyThreads(2);config.useSingleServer().setAddress("redis://"+host+":"+port).setDatabase(db).setPassword(password).setConnectionPoolSize(8).setConnectionMinimumIdleSize(1).setSubscriptionConnectionPoolSize(4).setSubscriptionConnectionMinimumIdleSize(1).setConnectTimeout(3000);return org.redisson.Redisson.create(config);});
 }
 public void add(String alias,GenericWebApplicationContext context,DispatcherServlet servlet){if(modules.putIfAbsent(alias,new Module(context,servlet))!=null)throw new IllegalStateException("Duplicate module");}
 public Module get(String alias){return modules.get(alias);}
 public Health health(){var result=Health.up();for(var entry:modules.entrySet()){try{var ctx=entry.getValue().context();for(var bean:ctx.getBeansOfType(HealthIndicator.class).entrySet()){if(bean.getKey().toLowerCase(java.util.Locale.ROOT).contains("readinessstate") || bean.getKey().toLowerCase(java.util.Locale.ROOT).contains("livenessstate"))continue;Health health=bean.getValue().health();if(!Status.UP.equals(health.getStatus()))return Health.down().withDetail("module",entry.getKey()).withDetail("component",bean.getKey()).build();}}catch(Exception e){return Health.down().withDetail("module",entry.getKey()).build();}}return result.withDetail("modules",modules.keySet()).build();}
 @PreDestroy public void close(){for(var module:modules.values())module.context().close();modules.clear();for(var client:lockClients.values())client.shutdown();lockClients.clear();}
}
