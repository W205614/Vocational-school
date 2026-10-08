package com.tianji.compact;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.AbstractProtocol;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Each call depth has its own bounded HTTP executor; waiting callers cannot consume its capacity. */
@Configuration(proxyBeanMethods=false)
public class InternalHttpConfiguration {
 @Bean(destroyMethod="close") InternalPools internalPools(){return new InternalPools();}
 static final class InternalPools implements AutoCloseable {
  final java.util.List<java.util.concurrent.ThreadPoolExecutor> executors=new java.util.ArrayList<>();
  public void close(){for(var executor:executors)executor.shutdown();}
 }
 @Bean WebServerFactoryCustomizer<TomcatServletWebServerFactory> internalHttp(Environment environment,InternalPools pools) {
  int first=environment.getRequiredProperty("tj.compact.internal-port",Integer.class);
  return factory->{
   for(int level=0;level<4;level++) {
    var connector=new Connector("org.apache.coyote.http11.Http11NioProtocol");
    connector.setPort(first+level);
    var protocol=(AbstractProtocol<?>)connector.getProtocolHandler();
    final int depth=level+1;
    var executor=new java.util.concurrent.ThreadPoolExecutor(32,32,30,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(32),task->{var thread=new Thread(task,"internal-http-"+depth);thread.setDaemon(true);return thread;});
    executor.allowCoreThreadTimeOut(true);pools.executors.add(executor);protocol.setExecutor(executor);
    protocol.setMaxConnections(256);protocol.setAcceptCount(32);protocol.setConnectionTimeout(1500);
    factory.addAdditionalConnectors(connector);
   }
  };
 }
}
