package com.tianji.gateway.swagger;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
@RestController
@RequiredArgsConstructor
public class SwaggerResourceController {
    private final GatewaySwaggerResourceProvider provider;
    @GetMapping("/swagger-resources")
    public Flux<GatewaySwaggerResourceProvider.Resource> resources() { return provider.get(); }
}
