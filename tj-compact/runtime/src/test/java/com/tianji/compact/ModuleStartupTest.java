package com.tianji.compact;

import org.junit.jupiter.api.Test;
import org.springframework.boot.*;
import org.springframework.core.Ordered;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.boot.health.contributor.*;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.health.DiscoveryClientHealthIndicator;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClient;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryProperties;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class ModuleStartupTest {
    static class First implements ApplicationRunner, Ordered {
        final List<String> calls;
        First(List<String> calls) { this.calls=calls; }
        public int getOrder() { return -10; }
        public void run(ApplicationArguments args) { calls.add("first:"+args.getOptionValues("fixture").getFirst()); }
    }
    @Test void childRunnersExecuteInOrderOnceBeforeReadiness() throws Exception {
        var calls=new ArrayList<String>();var registry=new ModuleRegistry();
        try(var context=new GenericWebApplicationContext()) {
            context.registerBean("command",CommandLineRunner.class,()->args->calls.add("command:"+args[0]));
            context.registerBean("first",First.class,()->new First(calls));context.refresh();
            registry.add("fixture",context,new DispatcherServlet(context));
            assertEquals(Status.DOWN,registry.health().getStatus());assertTrue(calls.isEmpty());
            var args=new DefaultApplicationArguments("--fixture=value");registry.start(args);registry.start(args);
            assertEquals(List.of("first:value","command:--fixture=value"),calls);
            assertEquals(Status.UP,registry.health().getStatus());
        } finally { registry.close(); }
    }
    @Test void runnerFailureClosesEveryChildAndCannotBeReportedAsReady() {
        var registry=new ModuleRegistry();var good=new GenericWebApplicationContext();var bad=new GenericWebApplicationContext();
        good.refresh();bad.registerBean("failure",ApplicationRunner.class,()->args->{throw new IllegalStateException("synthetic startup failure");});bad.refresh();
        registry.add("good",good,new DispatcherServlet(good));registry.add("bad",bad,new DispatcherServlet(bad));
        assertThrows(IllegalStateException.class,()->registry.start(new DefaultApplicationArguments()));
        assertFalse(good.isActive());assertFalse(bad.isActive());assertEquals(Status.DOWN,registry.health().getStatus());
        assertThrows(IllegalStateException.class,()->registry.start(new DefaultApplicationArguments()));
    }
    @Test void explicitlySoftDependenciesDegradeWithoutRemovingHardDependencyReadiness() throws Exception {
        var registry=new ModuleRegistry();var context=new GenericWebApplicationContext();
        context.setEnvironment(new org.springframework.mock.env.MockEnvironment().withProperty("tj.compact.readiness.required","db,redis"));
        context.registerBean("dbHealthIndicator",HealthIndicator.class,()->()->Health.up().build());
        context.registerBean("redisHealthIndicator",HealthIndicator.class,()->()->Health.up().build());
        context.registerBean("rabbitHealthIndicator",HealthIndicator.class,()->()->{throw new AssertionError("Optional upstream must not block readiness");});context.refresh();
        try {registry.add("fixture",context,new DispatcherServlet(context));registry.start(new DefaultApplicationArguments());
            assertEquals(Status.UP,registry.health().getStatus());assertTrue(registry.health().getDetails().get("optionalDependencies").toString().contains("rabbit"));
        } finally {registry.close();}
    }
    @Test void redissonUsesManagedTlsAndAclIdentityWithoutConnecting() throws Exception {
        var config=ModuleRegistry.lockConfiguration("redis.example.invalid",6380,0,"app","synthetic-password",true);
        String json=config.toJSON();assertTrue(json.contains("rediss://redis.example.invalid:6380"));assertTrue(json.contains("app"));
    }
    @Test void actualCommonsRegistrationProbeIsUnknownForStaticDiscoveryAndExplicitlyDisabledWithoutWeakeningDatabaseReadiness() throws Exception {
        for(boolean disabled:List.of(false,true)){
            var registry=new ModuleRegistry();var context=new GenericWebApplicationContext();var databaseUp=new java.util.concurrent.atomic.AtomicBoolean(true);
            var environment=new org.springframework.mock.env.MockEnvironment().withProperty("spring.cloud.nacos.discovery.enabled","false");
            if(disabled)environment.withProperty("spring.cloud.discovery.client.health-indicator.enabled","false");
            context.setEnvironment(environment);
            context.registerBean("discoveryClient",DiscoveryClient.class,()->new SimpleDiscoveryClient(new SimpleDiscoveryProperties()));
            new AnnotatedBeanDefinitionReader(context).register(CommonsClientAutoConfiguration.class);
            context.registerBean("dbHealthContributor",CompositeHealthContributor.class,()->CompositeHealthContributor.fromMap(Map.of("main",(HealthIndicator)()->databaseUp.get()?Health.up().build():Health.down().build())));
            context.refresh();
            try{
                registry.add("fixture",context,new DispatcherServlet(context));registry.start(new DefaultApplicationArguments());
                if(!disabled){
                    // The real Commons5 indicator waits for InstanceRegisteredEvent;
                    // static discovery/manual refresh intentionally emits no registration.
                    assertEquals(Status.UNKNOWN,context.getBean(DiscoveryClientHealthIndicator.class).health().getStatus());
                    Health health=registry.health();assertEquals(Status.DOWN,health.getStatus());
                    assertEquals("discoveryComposite/discoveryClient",health.getDetails().get("component"));
                    assertEquals("UNKNOWN",health.getDetails().get("componentStatus"));
                }else{
                    assertTrue(context.getBeansOfType(DiscoveryClientHealthIndicator.class).isEmpty());
                    assertFalse(context.containsBean("discoveryCompositeHealthContributor"));
                    assertEquals(Status.UP,registry.health().getStatus());databaseUp.set(false);
                    Health health=registry.health();assertEquals(Status.DOWN,health.getStatus());assertEquals("db/main",health.getDetails().get("component"));assertEquals("DOWN",health.getDetails().get("componentStatus"));
                }
            }finally{registry.close();}
        }
    }
    @Test void compositeDiagnosticsReportExactFailingLeafStatusWithoutDetailsOrExceptionMessages(CapturedOutput output)throws Exception{
        var registry=new ModuleRegistry();var context=new GenericWebApplicationContext();
        var result=new java.util.concurrent.atomic.AtomicReference<Health>(Health.unknown().withDetail("credential","synthetic-private-health-detail").build());
        context.registerBean("dbHealthContributor",CompositeHealthContributor.class,()->CompositeHealthContributor.fromMap(Map.of("pool",CompositeHealthContributor.fromMap(Map.of("main",(HealthIndicator)()->result.get())))));
        context.refresh();
        try{
            registry.add("fixture",context,new DispatcherServlet(context));registry.start(new DefaultApplicationArguments());
            Health health=registry.health();assertEquals(Status.DOWN,health.getStatus());assertEquals(Map.of("module","fixture","component","db/pool/main","componentStatus","UNKNOWN"),health.getDetails());
            result.set(Health.down().withDetail("credential","synthetic-private-health-detail").build());assertEquals("DOWN",registry.health().getDetails().get("componentStatus"));
            assertTrue(output.getAll().contains("module=fixture component=db/pool/main status=UNKNOWN"));assertFalse(output.getAll().contains("synthetic-private-health-detail"));
        }finally{registry.close();}
        var broken=new GenericWebApplicationContext();var failingRegistry=new ModuleRegistry();
        broken.registerBean("dbHealthIndicator",HealthIndicator.class,()->()->{throw new IllegalStateException("synthetic-private-exception-message");});broken.refresh();
        try{failingRegistry.add("fixture",broken,new DispatcherServlet(broken));failingRegistry.start(new DefaultApplicationArguments());
            Health health=failingRegistry.health();assertEquals(Status.DOWN,health.getStatus());assertEquals("ERROR",health.getDetails().get("componentStatus"));assertFalse(output.getAll().contains("synthetic-private-exception-message"));
        }finally{failingRegistry.close();}
    }
}
