package com.tianji.gateway.config;

import com.sun.net.httpserver.HttpServer;
import com.tianji.auth.common.util.JwtVerificationKeys;
import com.tianji.authsdk.gateway.util.AuthUtil;
import com.tianji.authsdk.gateway.util.JwtSignerHolder;
import com.tianji.common.utils.ServiceIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClient;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryProperties;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationHealthTest {
    @Test void keyExpiryRemovesGatewayFromReadinessUntilProviderRecovers() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        byte[] keys = new JwtVerificationKeys(List.of(generator.generateKeyPair().getPublic()))
                .jwks().getBytes(StandardCharsets.UTF_8);
        var status = new AtomicInteger(200);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/_modules/auth/jwks", exchange -> {
            exchange.sendResponseHeaders(status.get(), keys.length);
            try (var output = exchange.getResponseBody()) { output.write(keys); }
        });
        server.start();
        try {
            var environment = new MockEnvironment().withProperty("tj.routes.auth",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/_modules/auth");
            var holder = new JwtSignerHolder(new SimpleDiscoveryClient(new SimpleDiscoveryProperties()),
                    environment, new ServiceIdentity(Map.of("TJ_INTERNAL_TOKEN", "fixture-health-identity-0123456789")));
            var clock = new AtomicLong(1);
            ReflectionTestUtils.setField(holder, "clock", (LongSupplier) clock::get);
            var permissions = mock(AuthUtil.class);
            when(permissions.isReady()).thenReturn(true);
            var health = new AuthenticationHealth().authenticationHealthIndicator(holder, permissions);

            assertEquals(Status.OUT_OF_SERVICE, health.health().getStatus());
            holder.refresh();
            assertEquals(Status.UP, health.health().getStatus());

            status.set(503);
            clock.addAndGet(TimeUnit.SECONDS.toNanos(59));
            holder.refresh();
            assertEquals(Status.UP, health.health().getStatus());
            clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
            assertEquals(Status.OUT_OF_SERVICE, health.health().getStatus());

            status.set(200);
            holder.refresh();
            assertEquals(Status.UP, health.health().getStatus());
            when(permissions.isReady()).thenReturn(false);
            assertEquals(Status.OUT_OF_SERVICE, health.health().getStatus());
        } finally {
            server.stop(0);
        }
    }
}
