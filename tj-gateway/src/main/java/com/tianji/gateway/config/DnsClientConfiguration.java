package com.tianji.gateway.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import io.netty.channel.ChannelOption;
import java.time.Duration;
@Configuration(proxyBeanMethods=false)
public class DnsClientConfiguration {
    @Bean public HttpClientCustomizer boundedDnsCache() {
        return client->client.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,3000).resolver(resolver->resolver
                .cacheMinTimeToLive(Duration.ZERO)
                .cacheMaxTimeToLive(Duration.ofSeconds(10))
                .cacheNegativeTimeToLive(Duration.ofSeconds(2)));
    }
}
