package com.tianji.compact;
import com.sun.net.httpserver.*;
import com.tianji.api.config.FeignBulkheadCapability;
import com.tianji.common.utils.UserContext;
import feign.*;
import feign.Request;
import org.junit.jupiter.api.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** These tests cross a real socket and therefore exercise Feign's read deadline. */
class LocalModuleTransportTest {
 List<HttpServer> servers=new ArrayList<>();List<ExecutorService> executors=new ArrayList<>();
 Client client;int port;CountDownLatch arrived=new CountDownLatch(1),release=new CountDownLatch(1);
 org.apache.hc.client5.http.impl.classic.CloseableHttpClient http;
 final Map<String,Integer> commits=new ConcurrentHashMap<>();final AtomicInteger businessWrites=new AtomicInteger();
 @BeforeEach void setup()throws Exception {
  for(int attempt=0;attempt<100;attempt++) {
   port=30000+ThreadLocalRandom.current().nextInt(20000);
   try{for(int i=0;i<4;i++)servers.add(HttpServer.create(new InetSocketAddress("127.0.0.1",port+i),32));break;}
   catch(BindException error){servers.forEach(server->server.stop(0));servers.clear();}
  }
  assertEquals(4,servers.size());http=org.apache.hc.client5.http.impl.classic.HttpClients.createDefault();client=new FeignBulkheadCapability(8,Set.of(port)).enrich(new feign.hc5.ApacheHttp5Client(http));
  for(var server:servers){var pool=Executors.newSingleThreadExecutor();executors.add(pool);server.setExecutor(pool);server.createContext("/",this::handle);server.start();}
 }
 private void handle(HttpExchange exchange)throws IOException {
  try {
   String path=exchange.getRequestURI().getPath(),body;
   if(path.endsWith("/blocked")){arrived.countDown();release.await(3,TimeUnit.SECONDS);body="done";}
   else if(path.endsWith("/slow")){Thread.sleep(250);body="done";}
   else if(path.endsWith("/commit")) {
    String key=exchange.getRequestHeaders().getFirst("Idempotency-Key");
    int committed=commits.computeIfAbsent(key,ignored->{try{Thread.sleep(200);}catch(InterruptedException error){Thread.currentThread().interrupt();}return businessWrites.incrementAndGet();});body=Integer.toString(committed);
   }else if(path.endsWith("/nested")) {
    int depth=Integer.parseInt(exchange.getRequestHeaders().getFirst("X-TJ-Call-Depth"));
    body=depth==4?"nested-complete":read(client.execute(request("user","nested",Map.of("X-TJ-Call-Depth",List.of(Integer.toString(depth+1))),null),options(1000)));
   }else if(path.endsWith("/echo"))body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
   else body=exchange.getRequestHeaders().getFirst("user-info")+":"+exchange.getRequestHeaders().getFirst("request-id")+":"+exchange.getRequestURI().getRawQuery();
   byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
  }catch(InterruptedException error){Thread.currentThread().interrupt();}
  finally{UserContext.removeUser();exchange.close();}
 }
 @AfterEach void close()throws IOException{release.countDown();servers.forEach(server->server.stop(0));executors.forEach(ExecutorService::shutdownNow);if(http!=null)http.close();UserContext.removeUser();}
 private Request.Options options(int millis){return new Request.Options(millis,TimeUnit.MILLISECONDS,millis,TimeUnit.MILLISECONDS,true);}
 private Request request(String module,String path,Map<String,Collection<String>> headers,byte[] body){return Request.create(body==null?Request.HttpMethod.GET:Request.HttpMethod.POST,"http://127.0.0.1:"+port+"/_modules/"+module+"/"+path,headers,body,StandardCharsets.UTF_8,new RequestTemplate());}
 private String read(Response response)throws IOException{try(response){return new String(response.body().asInputStream().readAllBytes(),StandardCharsets.UTF_8);}}
 @Test void socketPreservesBodyQueryIdentityAndCallerState()throws Exception {
  UserContext.setUser(99L);UserContext.setRole(1L);UserContext.setSession("caller");
  assertEquals("7:trace-1:ids=1&ids=2",read(client.execute(request("user","lookup?ids=1&ids=2",Map.of("user-info",List.of("7"),"request-id",List.of("trace-1")),null),options(1000))));
  assertEquals("{\"value\":\"hello\"}",read(client.execute(request("user","echo",Map.of(),"{\"value\":\"hello\"}".getBytes(StandardCharsets.UTF_8)),options(1000))));
  assertEquals(99L,UserContext.getUser());assertEquals(1L,UserContext.getRole());assertEquals("caller",UserContext.getSession());
 }
 @Test void configuredReadDeadlineBoundsBlockedHttp(){assertThrows(IOException.class,()->client.execute(request("user","slow",Map.of(),null),options(50)));}
 @Test void timedOutCommitRetriedWithOriginalKeyIsWrittenOnce()throws Exception {
  var request=request("user","commit",Map.of("Idempotency-Key",List.of("original-key")),new byte[0]);
  assertThrows(IOException.class,()->client.execute(request,options(50)));assertEquals("1",read(client.execute(request,options(1000))));assertEquals(1,businessWrites.get());
 }
 @Test void nestedCallsUseReservedExecutorsRatherThanWaitingOnTheirCaller()throws Exception {
  assertEquals("nested-complete",read(client.execute(request("user","nested",Map.of("X-TJ-Call-Depth",List.of("1")),null),options(1000))));
  assertThrows(IOException.class,()->client.execute(request("user","nested",Map.of("X-TJ-Call-Depth",List.of("5")),null),options(1000)));
 }
 @Test void boundedAdmissionRejectsExcessAndReleasesPermitAfterFailure()throws Exception {
  client=new FeignBulkheadCapability(1,Set.of(port)).enrich(new feign.hc5.ApacheHttp5Client(http));
  try(var pool=Executors.newSingleThreadExecutor()) {
   var first=pool.submit(()->read(client.execute(request("user","blocked",Map.of(),null),options(2000))));assertTrue(arrived.await(1,TimeUnit.SECONDS));
   assertThrows(FeignException.TooManyRequests.class,()->client.execute(request("user","echo",Map.of(),new byte[0]),options(50)));
   release.countDown();assertEquals("done",first.get(3,TimeUnit.SECONDS));
   assertThrows(IOException.class,()->client.execute(request("user","slow",Map.of(),null),options(50)));
   assertEquals("recovered",read(client.execute(request("user","echo",Map.of(),"recovered".getBytes(StandardCharsets.UTF_8)),options(1000))));
  }
 }
}
