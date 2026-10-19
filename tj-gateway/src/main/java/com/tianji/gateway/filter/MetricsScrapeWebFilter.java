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
    public MetricsScrapeWebFilter(){this(()->System.getenv("TJ_INTERNAL_TOKEN"));}
    MetricsScrapeWebFilter(Supplier<String> configuredToken){this.configuredToken=configuredToken;}

    @Override public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){
        String path=exchange.getRequest().getPath().pathWithinApplication().elements().stream()
                .map(element->element instanceof PathContainer.PathSegment segment?segment.valueToMatch():element.value())
                .collect(Collectors.joining());
        if(!path.equals("/actuator/prometheus") && !path.equals("/actuator/prometheus/"))return chain.filter(exchange);
        String expected=configuredToken.get();var values=exchange.getRequest().getHeaders().getOrEmpty("X-Internal-Token");
        if(expected!=null && expected.length()>=32 && values.size()==1 &&
                MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),values.getFirst().getBytes(StandardCharsets.UTF_8)))
            return chain.filter(exchange);
        var response=exchange.getResponse();response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body="{\"code\":403,\"msg\":\"Trusted scrape identity required\",\"data\":null}".getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
