package com.tianji.compact;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.boot.health.contributor.*;
import org.springframework.context.annotation.Bean;
import java.util.*;
import jakarta.annotation.PreDestroy;
public final class ModuleRegistry {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ModuleRegistry.class);
 public record Module(GenericWebApplicationContext context,DispatcherServlet servlet){}
 private final Map<String,Module> modules=new java.util.concurrent.ConcurrentHashMap<>();
 private final Map<String,org.redisson.api.RedissonClient> lockClients=new java.util.concurrent.ConcurrentHashMap<>();
 private volatile boolean started;
 private boolean startupAttempted;
 private volatile String lastHealthFailure;
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
  for(var entry:new TreeMap<>(modules).entrySet())try{
   var ctx=entry.getValue().context();
   String configured=ctx.getEnvironment().getProperty("tj.compact.readiness.required", "*");
   Set<String> required=new HashSet<>(Arrays.asList(configured.split(",")));
   Set<String> found=new HashSet<>();
   for(var bean:new TreeMap<>(ctx.getBeansOfType(HealthContributor.class)).entrySet()){
    String name=bean.getKey().replaceFirst("(HealthIndicator|HealthContributor)$","");found.add(name);
    if(name.toLowerCase(Locale.ROOT).contains("readinessstate") || name.toLowerCase(Locale.ROOT).contains("livenessstate"))continue;
    if(!required.contains("*") && !required.contains(name)){degraded.add(entry.getKey()+":"+name);continue;}
    Failure failure=failure(bean.getValue(),name);
    if(failure!=null)return down(entry.getKey(),failure.component(),failure.status());
   }
   if(!required.contains("*") && !found.containsAll(required)){var missing=new TreeSet<>(required);missing.removeAll(found);return down(entry.getKey(),missing.first(),"MISSING");}
  }catch(Exception error){return down(entry.getKey(),"health-contributors","ERROR");}
  lastHealthFailure=null;
  return result.withDetail("modules",new TreeSet<>(modules.keySet())).withDetail("optionalDependencies",degraded).build();
 }
 private record Failure(String component,String status){}
 private static Failure failure(HealthContributor contributor,String path){
  try{
   if(contributor instanceof HealthIndicator indicator){var status=indicator.health().getStatus();return Status.UP.equals(status)?null:new Failure(path,safeStatus(status.getCode()));}
   if(contributor instanceof CompositeHealthContributor composite){
    var children=composite.stream().sorted(Comparator.comparing(HealthContributors.Entry::name)).toList();
    for(var child:children){var failed=failure(child.contributor(),path+"/"+child.name());if(failed!=null)return failed;}return null;
   }
   return new Failure(path,"UNKNOWN");
  }catch(Exception error){return new Failure(path,"ERROR");}
 }
 private Health down(String alias,String component,String status){
  String module=safeName(alias),name=safeName(component),code=safeStatus(status),signature=module+":"+name+":"+code;
  // Never log contributor details, status descriptions or exception messages:
  // these can contain JDBC URLs, broker identities or provider credentials.
  if(!signature.equals(lastHealthFailure)){lastHealthFailure=signature;log.warn("Compact readiness failure: module={} component={} status={}",module,name,code);}
  return Health.down().withDetail("module",module).withDetail("component",name).withDetail("componentStatus",code).build();
 }
 private static String safeName(String value){return value!=null&&value.matches("[A-Za-z0-9_.:/-]{1,160}")?value:"invalid-name";}
 private static String safeStatus(String value){return Set.of("UP","DOWN","OUT_OF_SERVICE","UNKNOWN","ERROR","MISSING").contains(value)?value:"UNRECOGNIZED";}
 @PreDestroy public void close(){started=false;RuntimeException failure=null;for(var module:modules.values())try{module.context().close();}catch(RuntimeException error){if(failure==null)failure=error;else failure.addSuppressed(error);}modules.clear();for(var client:lockClients.values())try{client.shutdown();}catch(RuntimeException error){if(failure==null)failure=error;else failure.addSuppressed(error);}lockClients.clear();if(failure!=null)throw failure;}
}
