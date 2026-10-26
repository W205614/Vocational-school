package com.tianji.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Actuator is a local WebFlux handler and never enters the Gateway GlobalFilter chain. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class MetricsScrapeWebFilter implements WebFilter {
    private final Supplier<String> configuredToken;
    private final java.util.function.BiPredicate<String,String> verifies;
    public MetricsScrapeWebFilter(){this.configuredToken=()->null;this.verifies=com.tianji.common.utils.ServiceIdentity.current()::accepts;}
    MetricsScrapeWebFilter(Supplier<String> configuredToken){this.configuredToken=configuredToken;this.verifies=(caller,given)->{String expected=configuredToken.get();return expected!=null && expected.length()>=32 && given!=null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),given.getBytes(StandardCharsets.UTF_8));};}

    @Override public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){
        String path=exchange.getRequest().getPath().pathWithinApplication().elements().stream()
                .map(element->element instanceof PathContainer.PathSegment segment?segment.valueToMatch():element.value())
                .collect(Collectors.joining());
        if(!path.equals("/actuator/prometheus") && !path.equals("/actuator/prometheus/"))return chain.filter(exchange);
        String expected=configuredToken.get();var values=exchange.getRequest().getHeaders().getOrEmpty("X-Internal-Token");
        var callers=exchange.getRequest().getHeaders().getOrEmpty("X-TJ-Service");
        if(values.size()==1 && callers.size()<=1 && verifies.test(callers.isEmpty()?null:callers.getFirst(),values.getFirst()))
            return chain.filter(exchange);
        var response=exchange.getResponse();response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body="{\"code\":403,\"msg\":\"Trusted scrape identity required\",\"data\":null}".getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
