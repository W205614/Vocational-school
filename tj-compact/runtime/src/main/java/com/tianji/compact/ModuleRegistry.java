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
 private volatile boolean started;
 private boolean startupAttempted;
 public org.redisson.api.RedissonClient locks(org.springframework.core.env.Environment env){
  String host=env.getRequiredProperty("spring.data.redis.host");int port=env.getProperty("spring.data.redis.port",Integer.class,6379),db=env.getProperty("spring.data.redis.database",Integer.class,0);String password=env.getProperty("spring.data.redis.password");
  String username=env.getProperty("spring.data.redis.username");boolean tls=env.getProperty("spring.data.redis.ssl.enabled",Boolean.class,false);
  String key=host+":"+port+":"+db+":"+tls+":"+java.util.Objects.toString(username,"")+":"+java.util.Objects.toString(password,"");
  return lockClients.computeIfAbsent(key,k->{var config=lockConfiguration(host,port,db,username,password,tls);return org.redisson.Redisson.create(config);});
 }
 static org.redisson.config.Config lockConfiguration(String host,int port,int db,String username,String password,boolean tls){
  var config=new org.redisson.config.Config();config.setThreads(2);config.setNettyThreads(2);
  config.useSingleServer().setAddress((tls?"rediss://":"redis://")+host+":"+port).setDatabase(db).setUsername(username).setPassword(password).setConnectionPoolSize(8).setConnectionMinimumIdleSize(1).setSubscriptionConnectionPoolSize(4).setSubscriptionConnectionMinimumIdleSize(1).setConnectTimeout(3000);
  return config;
 }
 public void add(String alias,GenericWebApplicationContext context,DispatcherServlet servlet){if(modules.putIfAbsent(alias,new Module(context,servlet))!=null)throw new IllegalStateException("Duplicate module");}
 public Module get(String alias){return modules.get(alias);}
 public synchronized void start(org.springframework.boot.ApplicationArguments arguments) throws Exception {
  if(started)return;
  if(startupAttempted || modules.isEmpty())throw new IllegalStateException("Modules are not ready for startup tasks");
  startupAttempted=true;
  try{ModuleStartup.run(modules.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->(org.springframework.context.ApplicationContext)e.getValue().context()).toList(),arguments);started=true;}
  catch(Exception | Error error){try{close();}catch(Exception cleanup){error.addSuppressed(cleanup);}throw error;}
 }
 public Health health(){
  if(!started)return Health.down().withDetail("startup","incomplete").build();
  var result=Health.up();var degraded=new TreeSet<String>();
  for(var entry:modules.entrySet())try{
   var ctx=entry.getValue().context();
   String configured=ctx.getEnvironment().getProperty("tj.compact.readiness.required", "*");
   Set<String> required=new HashSet<>(Arrays.asList(configured.split(",")));
   Set<String> found=new HashSet<>();
   for(var bean:ctx.getBeansOfType(HealthContributor.class).entrySet()){
    String name=bean.getKey().replaceFirst("(HealthIndicator|HealthContributor)$","");found.add(name);
    if(name.toLowerCase(Locale.ROOT).contains("readinessstate") || name.toLowerCase(Locale.ROOT).contains("livenessstate"))continue;
    if(!required.contains("*") && !required.contains(name)){degraded.add(entry.getKey()+":"+name);continue;}
    if(!healthy(bean.getValue())){
     if(required.contains("*") || required.contains(name))return Health.down().withDetail("module",entry.getKey()).withDetail("component",name).build();
     degraded.add(entry.getKey()+":"+name);
    }
   }
   if(!required.contains("*") && !found.containsAll(required))return Health.down().withDetail("module",entry.getKey()).withDetail("component","missing-required-contributor").build();
  }catch(Exception error){return Health.down().withDetail("module",entry.getKey()).build();}
  return result.withDetail("modules",new TreeSet<>(modules.keySet())).withDetail("optionalDependencies",degraded).build();
 }
 private static boolean healthy(HealthContributor contributor){
  if(contributor instanceof HealthIndicator indicator)return Status.UP.equals(indicator.health().getStatus());
  if(contributor instanceof CompositeHealthContributor composite){for(var child:composite)if(!healthy(child.contributor()))return false;return true;}
  return false;
 }
 @PreDestroy public void close(){started=false;RuntimeException failure=null;for(var module:modules.values())try{module.context().close();}catch(RuntimeException error){if(failure==null)failure=error;else failure.addSuppressed(error);}modules.clear();for(var client:lockClients.values())try{client.shutdown();}catch(RuntimeException error){if(failure==null)failure=error;else failure.addSuppressed(error);}lockClients.clear();if(failure!=null)throw failure;}
}
