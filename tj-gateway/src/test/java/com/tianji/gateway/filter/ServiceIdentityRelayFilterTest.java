package com.tianji.gateway.filter;

import com.tianji.common.utils.ServiceIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import java.nio.file.*;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

class ServiceIdentityRelayFilterTest {
    @TempDir Path directory;
    @Test void sharedModeAttachesItsCredentialOnlyAtRelayAndRemovesForgedCaller(){
        String token="synthetic-shared-token-"+"x".repeat(40);
        var identity=new ServiceIdentity(Map.of("TJ_INTERNAL_TOKEN",token));
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.post("/api/v2/auth/accounts/login").header("X-TJ-Service","identity").header("X-Internal-Token","forged"));
        exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR,URI.create("http://app-identity:24001/_modules/auth/accounts/login"));
        var reached=new AtomicBoolean();new ServiceIdentityRelayFilter(identity).filter(exchange,next->{
            assertNull(next.getRequest().getHeaders().getFirst("X-TJ-Service"));assertEquals(token,next.getRequest().getHeaders().getFirst("X-Internal-Token"));reached.set(true);return reactor.core.publisher.Mono.empty();
        }).block();assertTrue(reached.get());
    }
    @Test void forgedCallerIsReplacedWithOnlyTheResolvedReceiversCredential() throws Exception {
        String token="synthetic-support-edge-"+"x".repeat(40);Path file=directory.resolve("identities.json");
        Files.writeString(file,"{\"outbound\":{\"support\":\""+token+"\"},\"inbound\":{}}");
        var identity=new ServiceIdentity(Map.of("TJ_INTERNAL_AUTH_MODE","service-tokens","TJ_SERVICE_ID","gateway","TJ_SERVICE_TOKENS_FILE",file.toString()));
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/v2/services/media/cloud-content/fixture").header("X-TJ-Service","identity").header("X-Internal-Token","forged"));
        exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR,URI.create("http://app-support:24004/_modules/media/cloud-content/fixture"));
        var reached=new AtomicBoolean();new ServiceIdentityRelayFilter(identity).filter(exchange,next->{
            assertEquals("gateway",next.getRequest().getHeaders().getFirst("X-TJ-Service"));assertEquals(token,next.getRequest().getHeaders().getFirst("X-Internal-Token"));reached.set(true);return reactor.core.publisher.Mono.empty();
        }).block();assertTrue(reached.get());
        exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR,URI.create("http://app-commerce:24002/_modules/pay/orders"));
        assertThrows(IllegalStateException.class,()->new ServiceIdentityRelayFilter(identity).filter(exchange,next->reactor.core.publisher.Mono.empty()));
    }
}
