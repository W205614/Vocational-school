package com.tianji.authsdk.gateway.util;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.tianji.auth.common.util.JwtVerificationKeys;
import com.tianji.common.exceptions.ServiceUnavailableException;
import com.tianji.common.utils.ServiceIdentity;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.mock.env.MockEnvironment;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class JwtKeyRefreshTest {
    @Test void refreshBudgetMustFitInsideTheKeyTrustWindow() {
        var environment=new MockEnvironment().withProperty("tj.auth.jwk-max-age-seconds","10");
        assertThrows(IllegalArgumentException.class,()->new JwtSignerHolder(mock(DiscoveryClient.class),environment,new ServiceIdentity(Map.of("TJ_INTERNAL_TOKEN","fixture-rotation-identity-0123456789"))));
        environment.withProperty("tj.auth.jwk-retry-ms","1000");
        assertNotNull(new JwtSignerHolder(mock(DiscoveryClient.class),environment,new ServiceIdentity(Map.of("TJ_INTERNAL_TOKEN","fixture-rotation-identity-0123456789"))));
    }
    @Test void periodicRefreshReplacesWholeSetAndFailureHasBoundedTrust() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var old=generator.generateKeyPair();var next=generator.generateKeyPair();
        var body=new AtomicReference<>(new JwtVerificationKeys(List.of(old.getPublic())).jwks());
        var clock=new AtomicLong(1);var status=new AtomicInteger(200);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/_modules/auth/jwks",exchange->{
            assertEquals("format=jwks",exchange.getRequestURI().getQuery());
            byte[] bytes=body.get().getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status.get(),bytes.length);
            try(var output=exchange.getResponseBody()){output.write(bytes);}
        });server.start();
        try{
            var environment=new MockEnvironment().withProperty("tj.routes.auth","http://127.0.0.1:"+server.getAddress().getPort()+"/_modules/auth");
            var holder=new JwtSignerHolder(mock(DiscoveryClient.class),environment,new ServiceIdentity(Map.of("TJ_INTERNAL_TOKEN","fixture-rotation-identity-0123456789")),clock::get);
            assertThrows(ServiceUnavailableException.class,()->holder.verify(token(old)));
            holder.refresh();assertTrue(holder.verify(token(old)));assertFalse(holder.verify(token(next)));
            body.set(new JwtVerificationKeys(List.of(old.getPublic(),next.getPublic())).jwks());holder.refresh();
            assertTrue(holder.verify(token(old)));assertTrue(holder.verify(token(next)));
            body.set(new JwtVerificationKeys(List.of(next.getPublic())).jwks());holder.refresh();
            assertFalse(holder.verify(token(old)));assertTrue(holder.verify(token(next)));
            body.set("{\"keys\":[]}");holder.refresh();assertTrue(holder.verify(token(next)));
            status.set(503);clock.addAndGet(TimeUnit.SECONDS.toNanos(61));holder.refresh();
            assertNull(holder.getJwtSigner());
            assertThrows(ServiceUnavailableException.class,()->holder.verify(token(next)));
            status.set(200);body.set(new JwtVerificationKeys(List.of(next.getPublic())).jwks());holder.refresh();assertNotNull(holder.getJwtSigner());assertTrue(holder.verify(token(next)));
        }finally{server.stop(0);}
    }
    private JWT token(KeyPair key){return JWT.of(JWT.create().setHeader("kid",JwtVerificationKeys.id(key.getPublic())).setSigner(JWTSignerUtil.createSigner("rs256",key)).sign());}
}
