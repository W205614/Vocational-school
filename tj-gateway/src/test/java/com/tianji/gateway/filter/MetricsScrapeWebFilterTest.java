package com.tianji.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class MetricsScrapeWebFilterTest {
    private static final String TEST_IDENTITY="unit-test-metrics-scrape-identity-20261019";
    private WebTestClient client(String configured,AtomicInteger reached){
        return WebTestClient.bindToWebHandler(exchange->{
            reached.incrementAndGet();exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
            return exchange.getResponse().writeWith(reactor.core.publisher.Mono.just(exchange.getResponse().bufferFactory().wrap("local-handler-response".getBytes(StandardCharsets.UTF_8))));
        }).webFilter(new MetricsScrapeWebFilter(()->configured)).build();
    }
    @Test void missingOrInvalidIdentityCannotReachTheLocalActuatorHandler(){
        var reached=new AtomicInteger();var client=client(TEST_IDENTITY,reached);
        client.get().uri("/actuator/prometheus").exchange().expectStatus().isForbidden().expectBody().jsonPath("$.code").isEqualTo(403);
        for(String token:List.of("invalid",TEST_IDENTITY+"x",TEST_IDENTITY.substring(1)))
            client.get().uri("/actuator/prometheus").header("X-Internal-Token",token).header("user-role","1").exchange().expectStatus().isForbidden();
        assertEquals(0,reached.get());
    }
    @Test void configuredInternalIdentityCanScrapeTheLocalHandler(){
        var reached=new AtomicInteger();client(TEST_IDENTITY,reached).get().uri("/actuator/prometheus").header("X-Internal-Token",TEST_IDENTITY).exchange()
                .expectStatus().isOk().expectBody(String.class).isEqualTo("local-handler-response");assertEquals(1,reached.get());
    }
    @Test void duplicateInternalHeadersCannotSelectOneValidIdentity(){
        var reached=new AtomicInteger();client(TEST_IDENTITY,reached).get().uri("/actuator/prometheus").header("X-Internal-Token",TEST_IDENTITY,"invalid").exchange().expectStatus().isForbidden();assertEquals(0,reached.get());
    }
    @Test void absentOrShortServiceConfigurationFailsClosed(){
        for(String configured:new String[]{null,"short"}){
            var reached=new AtomicInteger();client(configured,reached).get().uri("/actuator/prometheus").header("X-Internal-Token",configured==null?TEST_IDENTITY:configured).exchange().expectStatus().isForbidden();assertEquals(0,reached.get());
        }
    }
    @Test void healthInfoAndApplicationRequestsKeepTheirExistingBehavior(){
        var reached=new AtomicInteger();var client=client(null,reached);
        for(String path:List.of("/actuator/health","/actuator/health/liveness","/actuator/health/readiness","/actuator/info","/api/v2/environment"))
            client.get().uri(path).exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("local-handler-response");
        assertEquals(5,reached.get());
    }
    @Test void decodedPathsMatrixParametersTrailingSlashAndQueryCannotBypassTheGuard(){
        var reached=new AtomicInteger();var client=client(TEST_IDENTITY,reached);
        for(String path:List.of("/actuator/%70rometheus","/actuator/prometheus;ignored=x","/actuator/prometheus/","/actuator/prometheus?format=plain"))
            client.get().uri(URI.create(path)).exchange().expectStatus().isForbidden();
        assertEquals(0,reached.get());
    }
}
