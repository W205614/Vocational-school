package com.zhixuetang.compact;

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
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(InternalHttpConfiguration.class)
public class CompactHost {
    private static String[] aliases;
    private static final Map<String, String> CLASSES =
            Map.ofEntries(
                    Map.entry("auth", "com.zhixuetang.auth.AuthApplication"),
                    Map.entry("user", "com.zhixuetang.user.UserApplication"),
                    Map.entry("course", "com.zhixuetang.course.CourseApplication"),
                    Map.entry("learning", "com.zhixuetang.learning.LearningApplication"),
                    Map.entry("exam", "com.zhixuetang.exam.ExamApplication"),
                    Map.entry("remark", "com.zhixuetang.remark.RemarkApplication"),
                    Map.entry("trade", "com.zhixuetang.trade.TradeApplication"),
                    Map.entry("pay", "com.zhixuetang.pay.PayApplication"),
                    Map.entry("promotion", "com.zhixuetang.promotion.PromotionApplication"),
                    Map.entry("media", "com.zhixuetang.media.MediaApplication"),
                    Map.entry("search", "com.zhixuetang.search.SearchApplication"),
                    Map.entry("message", "com.zhixuetang.message.MessageApplication"),
                    Map.entry("data", "com.zhixuetang.data.DataCenterApplication"));

    public static void run(String group, String[] modules, String[] args) {
        aliases = modules.clone();
        var app = new SpringApplication(CompactHost.class);
        app.setDefaultProperties(
                Map.of(
                        "spring.application.name",
                        group + "-app",
                        "spring.config.name",
                        "compact",
                        "zxt.reliability.enabled",
                        "false",
                        "spring.main.allow-bean-definition-overriding",
                        "false"));
        app.run(args);
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<
                    com.zhixuetang.common.filters.RequestIdFilter>
            requestIds() {
        var bean =
                new org.springframework.boot.web.servlet.FilterRegistrationBean<>(
                        new com.zhixuetang.common.filters.RequestIdFilter());
        bean.setOrder(-200);
        return bean;
    }

    @Bean
    public ModuleRegistry moduleRegistry() {
        return new ModuleRegistry();
    }

    @Bean
    public ApplicationRunner moduleStartupTasks(ModuleRegistry registry) {
        return registry::start;
    }

    @Bean
    public ServletContextInitializer moduleInitializer(Environment host, ModuleRegistry registry) {
        return servlet -> {
            Path directory = Path.of(host.getRequiredProperty("zxt.compact.config-directory"));
            for (String alias : aliases) {
                var yaml = new YamlPropertiesFactoryBean();
                yaml.setResources(new FileSystemResource(directory.resolve(alias + ".yml")));
                Properties values = Objects.requireNonNull(yaml.getObject());
                try {
                    var resources =
                            new org.springframework.core.io.support
                                            .PathMatchingResourcePatternResolver()
                                    .getResources("classpath*:mapper/**/*.xml");
                    String artifact =
                            switch (alias) {
                                case "auth" -> "zxt-auth-service";
                                case "pay" -> "zxt-pay-service";
                                case "message" -> "zxt-message-service";
                                default -> "zxt-" + alias;
                            };
                    List<String> owned = new ArrayList<>();
                    for (var resource : resources) {
                        String url = resource.getURL().toExternalForm();
                        if (url.contains(artifact + "-") || url.contains("/" + artifact + "/"))
                            owned.add(url);
                    }
                    values.setProperty("mybatis-plus.mapper-locations", String.join(",", owned));
                } catch (java.io.IOException error) {
                    throw new IllegalStateException("Cannot isolate mapper resources", error);
                }

                var environment =
                        new org.springframework.web.context.support.StandardServletEnvironment();
                environment
                        .getPropertySources()
                        .addFirst(new PropertiesPropertySource("module-" + alias, values));
                environment.setActiveProfiles(
                        values.getProperty("spring.profiles.active", "local-simulator").split(","));
                var child = new GenericWebApplicationContext();
                child.getDefaultListableBeanFactory().setAllowBeanDefinitionOverriding(false);
                child.setId("module-" + alias);
                child.setEnvironment(environment);
                child.setServletContext(servlet);
                child.setDisplayName(alias);
                child.registerBean(
                        "redissonClient",
                        org.redisson.api.RedissonClient.class,
                        () -> registry.locks(environment),
                        definition -> definition.setDestroyMethodName(""));
                try {
                    new org.springframework.context.annotation.AnnotatedBeanDefinitionReader(child)
                            .register(Class.forName(CLASSES.get(alias)));
                    child.refresh();
                    var dispatcher = new DispatcherServlet(child);
                    var registration = servlet.addServlet("module-" + alias, dispatcher);
                    registration.addMapping("/_modules/" + alias + "/*");
                    registration.setLoadOnStartup(1);
                    registration.setMultipartConfig(
                            new MultipartConfigElement(
                                    "", 200L * 1024 * 1024, 201L * 1024 * 1024, 0));
                    registry.add(alias, child, dispatcher);
                } catch (Exception e) {
                    child.close();
                    registry.close();
                    throw new IllegalStateException("Module startup failed: " + alias, e);
                }
            }
        };
    }

    @Bean
    public org.springframework.boot.health.contributor.HealthIndicator modulesHealthIndicator(
            ModuleRegistry registry) {
        return () -> registry.health();
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<ModuleGuard> moduleGuard(
            Environment environment) {
        var bean =
                new org.springframework.boot.web.servlet.FilterRegistrationBean<>(
                        new ModuleGuard(
                                environment.getRequiredProperty(
                                        "zxt.compact.internal-port", Integer.class)));
        bean.setOrder(-100);
        return bean;
    }
}
