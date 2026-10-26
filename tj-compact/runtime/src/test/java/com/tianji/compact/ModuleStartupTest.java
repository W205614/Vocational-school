package com.tianji.compact;

import org.junit.jupiter.api.Test;
import org.springframework.boot.*;
import org.springframework.core.Ordered;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.boot.health.contributor.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

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
}
