package com.tianji.search.config;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.apache.hc.core5.util.Timeout;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import java.net.URI;
import java.util.Arrays;

@Configuration(proxyBeanMethods=false)
public class SearchConnectionConfiguration {
 @Bean(destroyMethod="close") public Rest5ClientTransport searchTransport(@Value("${spring.elasticsearch.uris}") String uris) {
  var hosts=Arrays.stream(uris.split(",")).map(String::trim).map(URI::create).toArray(URI[]::new);
  var client=Rest5Client.builder(hosts)
   .setConnectionConfigCallback(c->c.setConnectTimeout(Timeout.ofMilliseconds(1500)).setSocketTimeout(Timeout.ofMilliseconds(5000)))
   .setRequestConfigCallback(c->c.setConnectionRequestTimeout(Timeout.ofMilliseconds(1000)).setResponseTimeout(Timeout.ofMilliseconds(5000))).build();
  return new Rest5ClientTransport(client,new JacksonJsonpMapper());
 }
 @Bean public ElasticsearchClient elasticsearchClient(Rest5ClientTransport transport){return new ElasticsearchClient(transport);}
}
