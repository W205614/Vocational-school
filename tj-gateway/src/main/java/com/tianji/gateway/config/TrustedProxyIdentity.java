package com.tianji.gateway.config;

import io.netty.util.NetUtil;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Only explicitly configured proxy peers may supply the single-hop forwarding headers. */
@Component
public class TrustedProxyIdentity {
    public record Identity(String address, String protocol) {}
    @FunctionalInterface interface Resolver { InetAddress[] resolve(String name) throws UnknownHostException; }
    private record Cached(Set<InetAddress> addresses, long expires) {}
    private final List<String> trusted;
    private final long ttl;
    private final Resolver resolver;
    private final LongSupplier clock;
    private final Map<String,Cached> cache=new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public TrustedProxyIdentity(Environment environment) {
        this(Binder.get(environment).bind("tj.gateway.proxy.trusted-addresses", Bindable.listOf(String.class)).orElse(List.of()),
                environment.getProperty("tj.gateway.proxy.dns-cache-seconds",Long.class,30L),InetAddress::getAllByName,System::nanoTime);
        if(!"none".equalsIgnoreCase(environment.getProperty("server.forward-headers-strategy","none")))
            throw new IllegalStateException("Gateway must use server.forward-headers-strategy=none before trusted proxy identity is checked");
    }
    TrustedProxyIdentity(List<String> trusted,long seconds,Resolver resolver,LongSupplier clock) {
        if(seconds<1 || seconds>300)throw new IllegalArgumentException("Proxy DNS cache must be between 1 and 300 seconds");
        for(String peer:trusted)if(peer==null || (!literal(peer) && !peer.matches("(?=.{1,253}$)[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?")))
            throw new IllegalArgumentException("Trusted proxies must be explicit IP addresses or DNS service names");
        this.trusted=List.copyOf(trusted);this.ttl=Duration.ofSeconds(seconds).toNanos();this.resolver=resolver;this.clock=clock;
    }
    public Identity identify(ServerHttpRequest request) {
        var remote=request.getRemoteAddress();InetAddress peer=remote==null?null:remote.getAddress();
        Identity fallback=new Identity(peer==null?"unknown":peer.getHostAddress(),"https".equalsIgnoreCase(request.getURI().getScheme())?"https":"http");
        if(peer==null || trusted.stream().noneMatch(name->addresses(name).contains(peer)))return fallback;
        String forwarded=request.getHeaders().getFirst("X-Forwarded-For"),protocol=request.getHeaders().getFirst("X-Forwarded-Proto");
        // Nginx overwrites these headers. A chain, duplicate header or DNS name is not a client IP.
        if(request.getHeaders().getOrEmpty("X-Forwarded-For").size()!=1 || request.getHeaders().getOrEmpty("X-Forwarded-Proto").size()!=1 || !literal(forwarded)
                || !("http".equals(protocol) || "https".equals(protocol)))return fallback;
        try{return new Identity(InetAddress.getByName(forwarded).getHostAddress(),protocol);}
        catch(UnknownHostException ignored){return fallback;}
    }
    private Set<InetAddress> addresses(String name) {
        long now=clock.getAsLong();
        return cache.compute(name,(key,prior)->{
            if(prior!=null && now-prior.expires()<0)return prior;
            try{return new Cached(Set.copyOf(Arrays.asList(resolver.resolve(key))),now+ttl);}
            catch(UnknownHostException ignored){return new Cached(Set.of(),now+ttl);}
        }).addresses();
    }
    private static boolean literal(String value){return value!=null && (NetUtil.isValidIpV4Address(value) || NetUtil.isValidIpV6Address(value));}
}
