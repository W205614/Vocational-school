package com.tianji.authsdk.gateway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import com.tianji.authsdk.gateway.util.JwtSignerHolder;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.security.KeyPairGenerator;
import java.util.Base64;
class JwtSignerPrefixTest {
 @Test void configuredModulePrefixIsRetainedWhenFetchingKey() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  var key=KeyPairGenerator.getInstance("RSA");key.initialize(2048);var bytes=Base64.getEncoder().encode(key.generateKeyPair().getPublic().getEncoded());
  server.createContext("/_modules/auth/jwks",exchange->{exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
  try{var env=new MockEnvironment().withProperty("tj.routes.auth","http://127.0.0.1:"+server.getAddress().getPort()+"/_modules/auth");var holder=new JwtSignerHolder(mock(DiscoveryClient.class),env);holder.refresh();assertNotNull(holder.getJwtSigner());}finally{server.stop(0);}
 }
}
