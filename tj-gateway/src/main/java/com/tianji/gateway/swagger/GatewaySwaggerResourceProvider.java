package com.tianji.gateway.swagger;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
@Component
@RequiredArgsConstructor
public class GatewaySwaggerResourceProvider {
    private final RouteLocator routeLocator;
    public record Resource(String name, String url) {}
    public Flux<Resource> get() {
        return routeLocator.getRoutes().filter(r -> "lb".equals(r.getUri().getScheme()))
                .map(r -> new Resource(r.getUri().getHost(), "/" + r.getId() + "/v3/api-docs"));
    }
}
