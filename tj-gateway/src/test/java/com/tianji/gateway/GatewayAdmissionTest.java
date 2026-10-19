package com.tianji.gateway;

import com.tianji.authsdk.gateway.util.AuthUtil;
import com.tianji.gateway.config.AccessPolicy;
import com.tianji.gateway.config.AuthProperties;
import com.tianji.gateway.filter.AccountAuthFilter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named="TJ_INTERNAL_TOKEN",matches=".+")
class GatewayAdmissionTest {
    AccountAuthFilter filter;
    Semaphore capacity;
    Scheduler scheduler;
    @BeforeEach void setup()throws Exception {
        scheduler=Schedulers.newBoundedElastic(4,16,"admission-test");
        filter=new AccountAuthFilter(mock(AuthUtil.class),new AuthProperties(),new AccessPolicy(),scheduler,new com.tianji.gateway.config.TrustedProxyIdentity(new org.springframework.mock.env.MockEnvironment()));
        capacity=(Semaphore)ReflectionTestUtils.getField(filter,"requests");
        capacity.acquire(198);
    }
    @AfterEach void close(){scheduler.dispose();}
    @Test void publicRoutesStripForgedIdentityAndProtocolHeaders(){
        var request=MockServerHttpRequest.get("/api/v2/environment")
                .remoteAddress(new java.net.InetSocketAddress("198.51.100.7",4567))
                .header("user-info","1").header("user-role","1").header("user-session","forged")
                .header("X-Internal-Token","forged").header("X-Client-IP","203.0.113.1").header("X-Client-Proto","https")
                .header("X-Forwarded-For","203.0.113.1").header("X-Forwarded-Proto","https").header("Forwarded","for=203.0.113.1;proto=https").build();
        filter.filter(MockServerWebExchange.from(request),exchange->{
            var headers=exchange.getRequest().getHeaders();assertEquals("198.51.100.7",headers.getFirst("X-Client-IP"));assertEquals("http",headers.getFirst("X-Client-Proto"));
            for(String header:java.util.List.of("user-info","user-role","user-session","Forwarded","X-Forwarded-For","X-Forwarded-Proto"))assertNull(headers.getFirst(header));
            assertEquals(System.getenv("TJ_INTERNAL_TOKEN"),headers.getFirst("X-Internal-Token"));return reactor.core.publisher.Mono.empty();
        }).block(Duration.ofSeconds(2));
    }
    @Test void trustedProxyIdentityAndHttpsReachAuthAsInternalHeaders(){
        var proxies=new com.tianji.gateway.config.TrustedProxyIdentity(new org.springframework.mock.env.MockEnvironment().withProperty("tj.gateway.proxy.trusted-addresses","172.20.0.2"));
        var trusted=new AccountAuthFilter(mock(AuthUtil.class),new AuthProperties(),new AccessPolicy(),scheduler,proxies);
        var request=MockServerHttpRequest.post("/api/v2/auth/accounts/login")
                .remoteAddress(new java.net.InetSocketAddress("172.20.0.2",4567))
                .header("X-Forwarded-For","203.0.113.10").header("X-Forwarded-Proto","https").header("X-Client-IP","forged").build();
        trusted.filter(MockServerWebExchange.from(request),exchange->{
            var headers=exchange.getRequest().getHeaders();assertEquals("203.0.113.10",headers.getFirst("X-Client-IP"));assertEquals("https",headers.getFirst("X-Client-Proto"));
            assertNull(headers.getFirst("X-Forwarded-For"));assertEquals(System.getenv("TJ_INTERNAL_TOKEN"),headers.getFirst("X-Internal-Token"));return reactor.core.publisher.Mono.empty();
        }).block(Duration.ofSeconds(2));
    }
    private MockServerWebExchange exchange(){return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v2/environment").build());}
    @Test void shortSaturationWaitsForCompletionWithoutRejectingNormalWork()throws Exception {
        var entered=new CountDownLatch(2);var next=new CountDownLatch(1);
        var firstRelease=Sinks.<Void>one();var secondRelease=Sinks.<Void>one();
        var first=filter.filter(exchange(),e->{entered.countDown();return firstRelease.asMono();}).toFuture();
        var second=filter.filter(exchange(),e->{entered.countDown();return secondRelease.asMono();}).toFuture();
        try {
            assertTrue(entered.await(1,TimeUnit.SECONDS));
            var third=filter.filter(exchange(),e->{next.countDown();return reactor.core.publisher.Mono.empty();}).toFuture();
            assertFalse(next.await(50,TimeUnit.MILLISECONDS));assertFalse(third.isDone(),"Normal work should wait briefly for admission");
            firstRelease.tryEmitEmpty();first.get(1,TimeUnit.SECONDS);
            assertTrue(next.await(1,TimeUnit.SECONDS));third.get(1,TimeUnit.SECONDS);
        }finally{firstRelease.tryEmitEmpty();secondRelease.tryEmitEmpty();first.get(1,TimeUnit.SECONDS);second.get(1,TimeUnit.SECONDS);}
        assertEquals(2,capacity.availablePermits());
    }
    @Test void cancellationWhileWaitingDoesNotLeakOrCreateAdmissionCapacity()throws Exception {
        capacity.acquire(2);
        var waiting=filter.filter(exchange(),e->reactor.core.publisher.Mono.empty()).toFuture();
        try {
            Thread.sleep(50);assertTrue(waiting.cancel(true));assertEquals(0,capacity.availablePermits());
        }finally{capacity.release(2);}
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(capacity.availablePermits()!=2 && System.nanoTime()<deadline)Thread.sleep(10);
        filter.filter(exchange(),e->reactor.core.publisher.Mono.empty()).block(Duration.ofSeconds(2));
        assertEquals(2,capacity.availablePermits());
    }
    @Test void authorizationExecutorCanAcceptTheAgreedTwoHundredUserBurst()throws Exception {
        var configuration=new com.tianji.gateway.config.AuthorizationExecutorConfiguration();
        var pool=configuration.authorizationExecutor();pool.initialize();
        var configuredScheduler=configuration.authorizationScheduler(pool);
        var actual=new AccountAuthFilter(mock(AuthUtil.class),new AuthProperties(),new AccessPolicy(),configuredScheduler,new com.tianji.gateway.config.TrustedProxyIdentity(new org.springframework.mock.env.MockEnvironment()));
        var entered=new CountDownLatch(8);var release=new CountDownLatch(1);
        var futures=new java.util.ArrayList<CompletableFuture<Void>>();
        try {
            for(int index=0;index<200;index++)futures.add(actual.filter(exchange(),e->{
                entered.countDown();
                try{if(!release.await(2,TimeUnit.SECONDS))throw new IllegalStateException("Blocked authorization test deadline");}
                catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}
                return reactor.core.publisher.Mono.empty();
            }).toFuture());
            assertTrue(entered.await(1,TimeUnit.SECONDS));
            assertFalse(futures.stream().anyMatch(CompletableFuture::isCompletedExceptionally),"The declared normal load must fit in the bounded authorization executor");
            release.countDown();for(var future:futures)future.get(2,TimeUnit.SECONDS);
        }finally{release.countDown();configuredScheduler.dispose();pool.shutdown();}
    }
}
