package com.tianji.search.config;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.apache.http.HttpHost;
import org.elasticsearch.client.*;
import java.util.Arrays;
@Configuration(proxyBeanMethods=false)
public class SearchConnectionConfiguration {
 @Bean(destroyMethod="close") public RestHighLevelClient restHighLevelClient(@Value("${spring.elasticsearch.uris}") String uris){
  var hosts=Arrays.stream(uris.split(",")).map(String::trim).map(HttpHost::create).toArray(HttpHost[]::new);
  var client=RestClient.builder(hosts).setRequestConfigCallback(builder->builder.setConnectTimeout(1500).setSocketTimeout(5000).setConnectionRequestTimeout(1000));
  return new RestHighLevelClient(client);
 }
}
