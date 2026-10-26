package com.tianji.gateway.filter;

import com.tianji.common.utils.ServiceIdentity;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.net.URI;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/** Attach identity after route rewriting, so the credential belongs to the actual receiver. */
@Component
public final class ServiceIdentityRelayFilter implements GlobalFilter, Ordered {
    private final ServiceIdentity identity;
    public ServiceIdentityRelayFilter(){this(ServiceIdentity.current());}
    ServiceIdentityRelayFilter(ServiceIdentity identity){this.identity=identity;}
    @Override public int getOrder(){return 10001;}
    @Override public Mono<Void> filter(ServerWebExchange exchange,GatewayFilterChain chain){
        URI destination=exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        if(destination==null)return Mono.error(new IllegalStateException("Destination service is not resolved"));
        var credential=identity.outgoing(destination.toString());
        return chain.filter(exchange.mutate().request(builder->builder.headers(headers->{
            headers.remove("X-Internal-Token");headers.remove("X-TJ-Service");
            headers.set("X-Internal-Token",credential.token());
            if(credential.caller()!=null)headers.set("X-TJ-Service",credential.caller());
        })).build());
    }
}
