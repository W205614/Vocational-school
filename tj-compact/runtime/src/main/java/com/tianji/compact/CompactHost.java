package com.tianji.compact;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.env.*;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import jakarta.servlet.*;
import java.nio.file.*;
import java.util.*;
/** One server/JVM; each module owns a separate bean factory, datasource and transaction manager. */
@Configuration(proxyBeanMethods=false)
@EnableAutoConfiguration
@Import(InternalHttpConfiguration.class)
public class CompactHost {
 private static String[] aliases;
 private static final Map<String,String> CLASSES=Map.ofEntries(Map.entry("auth","com.tianji.auth.AuthApplication"),Map.entry("user","com.tianji.user.UserApplication"),Map.entry("course","com.tianji.course.CourseApplication"),Map.entry("learning","com.tianji.learning.LearningApplication"),Map.entry("exam","com.tianji.exam.ExamApplication"),Map.entry("remark","com.tianji.remark.RemarkApplication"),Map.entry("trade","com.tianji.trade.TradeApplication"),Map.entry("pay","com.tianji.pay.PayApplication"),Map.entry("promotion","com.tianji.promotion.PromotionApplication"),Map.entry("media","com.tianji.media.MediaApplication"),Map.entry("search","com.tianji.search.SearchApplication"),Map.entry("message","com.tianji.message.MessageApplication"),Map.entry("data","com.tianji.data.DataCenterApplication"));
 public static void run(String group,String[] modules,String[] args){aliases=modules.clone();var app=new SpringApplication(CompactHost.class);app.setDefaultProperties(Map.of("spring.application.name",group+"-app","spring.config.name","compact","tj.reliability.enabled","false","spring.main.allow-bean-definition-overriding","false"));app.run(args);}
 @Bean public org.springframework.boot.web.servlet.FilterRegistrationBean<com.tianji.common.filters.RequestIdFilter> requestIds(){var bean=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(new com.tianji.common.filters.RequestIdFilter());bean.setOrder(-200);return bean;}
 @Bean public ModuleRegistry moduleRegistry(){return new ModuleRegistry();}
 @Bean public ServletContextInitializer moduleInitializer(Environment host,ModuleRegistry registry){return servlet->{
  Path directory=Path.of(host.getRequiredProperty("tj.compact.config-directory"));
  for(String alias:aliases){
   var yaml=new YamlPropertiesFactoryBean();yaml.setResources(new FileSystemResource(directory.resolve(alias+".yml")));Properties values=Objects.requireNonNull(yaml.getObject());
   try {var resources=new org.springframework.core.io.support.PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml");String artifact=switch(alias){case "auth"->"tj-auth-service";case "pay"->"tj-pay-service";case "message"->"tj-message-service";default->"tj-"+alias;};List<String> owned=new ArrayList<>();for(var resource:resources){String url=resource.getURL().toExternalForm();if(url.contains(artifact+"-") || url.contains("/"+artifact+"/"))owned.add(url);}values.setProperty("mybatis-plus.mapper-locations",String.join(",",owned));}catch(java.io.IOException error){throw new IllegalStateException("Cannot isolate mapper resources",error);}

   var environment=new org.springframework.web.context.support.StandardServletEnvironment();environment.getPropertySources().addFirst(new PropertiesPropertySource("module-"+alias,values));environment.setActiveProfiles(values.getProperty("spring.profiles.active","local-simulator").split(","));
   var child=new GenericWebApplicationContext();child.getDefaultListableBeanFactory().setAllowBeanDefinitionOverriding(false);child.setId("module-"+alias);child.setEnvironment(environment);child.setServletContext(servlet);child.setDisplayName(alias);child.registerBean("redissonClient",org.redisson.api.RedissonClient.class,()->registry.locks(environment),definition->definition.setDestroyMethodName(""));
   try{new org.springframework.context.annotation.AnnotatedBeanDefinitionReader(child).register(Class.forName(CLASSES.get(alias)));child.refresh();var dispatcher=new DispatcherServlet(child);var registration=servlet.addServlet("module-"+alias,dispatcher);registration.addMapping("/_modules/"+alias+"/*");registration.setLoadOnStartup(1);registration.setMultipartConfig(new MultipartConfigElement("",200L*1024*1024,201L*1024*1024,0));registry.add(alias,child,dispatcher);}catch(Exception e){child.close();registry.close();throw new IllegalStateException("Module startup failed: "+alias,e);}
  }
 };}
 @Bean public org.springframework.boot.health.contributor.HealthIndicator modulesHealthIndicator(ModuleRegistry registry){return ()->registry.health();}
 @Bean public org.springframework.boot.web.servlet.FilterRegistrationBean<ModuleGuard> moduleGuard(Environment environment){var bean=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(new ModuleGuard(environment.getRequiredProperty("tj.compact.internal-port",Integer.class)));bean.setOrder(-100);return bean;}
}
