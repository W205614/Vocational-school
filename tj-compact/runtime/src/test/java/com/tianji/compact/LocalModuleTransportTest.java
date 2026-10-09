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
 List<FeignBulkheadCapability> capabilities=new ArrayList<>();
 org.apache.hc.client5.http.impl.classic.CloseableHttpClient http;
 final Map<String,Integer> commits=new ConcurrentHashMap<>();final AtomicInteger businessWrites=new AtomicInteger();
 final List<IOException> serverFailures=new CopyOnWriteArrayList<>();
 volatile boolean saturatedChains;final CountDownLatch[] levels={new CountDownLatch(2),new CountDownLatch(2),new CountDownLatch(2)};
 @BeforeEach void setup()throws Exception {
  for(int attempt=0;attempt<100;attempt++) {
   port=30000+ThreadLocalRandom.current().nextInt(20000);
   try{for(int i=0;i<4;i++)servers.add(HttpServer.create(new InetSocketAddress("127.0.0.1",port+i),32));break;}
   catch(BindException error){servers.forEach(server->server.stop(0));servers.clear();}
  }
  assertEquals(4,servers.size());http=org.apache.hc.client5.http.impl.classic.HttpClients.createDefault();client=limited(8);
  for(var server:servers){var pool=Executors.newFixedThreadPool(2);executors.add(pool);server.setExecutor(pool);server.createContext("/",this::handle);server.start();}
 }
 private void handle(HttpExchange exchange)throws IOException {
  try {
   String path=exchange.getRequestURI().getPath(),body;
   if(path.endsWith("/slow-body")){
    exchange.sendResponseHeaders(200,4);
    exchange.getResponseBody().write('d');exchange.getResponseBody().flush();
    if(!release.await(10,TimeUnit.SECONDS))throw new IOException("Stalled-body test did not release its server");
    exchange.getResponseBody().write("one".getBytes(StandardCharsets.UTF_8));return;
   }
   if(path.endsWith("/blocked")){arrived.countDown();release.await(3,TimeUnit.SECONDS);body="done";}
   else if(path.endsWith("/slow")){Thread.sleep(250);body="done";}
   else if(path.endsWith("/commit")) {
    String key=exchange.getRequestHeaders().getFirst("Idempotency-Key");
    int committed=commits.computeIfAbsent(key,ignored->{try{Thread.sleep(200);}catch(InterruptedException error){Thread.currentThread().interrupt();}return businessWrites.incrementAndGet();});body=Integer.toString(committed);
   }else if(path.endsWith("/nested")) {
    int depth=Integer.parseInt(exchange.getRequestHeaders().getFirst("X-TJ-Call-Depth"));
    if(saturatedChains && depth<4){levels[depth-1].countDown();if(!levels[depth-1].await(2,TimeUnit.SECONDS))throw new IOException("Peer nested call failed to arrive");if(depth==1)Thread.sleep(250);}
    String module=path.split("/")[2];
    body=depth==4?"nested-complete":read(client.execute(request(module,"nested",Map.of("X-TJ-Call-Depth",List.of(Integer.toString(depth+1))),null),options(1500)));
   }else if(path.endsWith("/echo"))body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
   else body=exchange.getRequestHeaders().getFirst("user-info")+":"+exchange.getRequestHeaders().getFirst("request-id")+":"+exchange.getRequestURI().getRawQuery();
   byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
  }catch(InterruptedException error){Thread.currentThread().interrupt();}
  catch(IOException error){serverFailures.add(error);throw error;}
  finally{UserContext.removeUser();exchange.close();}
 }
 @AfterEach void close()throws IOException{release.countDown();servers.forEach(server->server.stop(0));executors.forEach(ExecutorService::shutdownNow);for(var capability:capabilities)capability.close();if(http!=null)http.close();UserContext.removeUser();}
 private Client limited(int maximum){var capability=new FeignBulkheadCapability(maximum,Set.of(port));capabilities.add(capability);return capability.enrich(new feign.hc5.ApacheHttp5Client(http));}
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
 @Test void readDeadlineAlsoBoundsAStalledBodyAndClosingItReleasesAdmission()throws Exception {
  client=limited(1);
  // Deliver a byte before blocking so the timeout necessarily occurs in body
  // decoding, after headers and admission have been obtained. The latch keeps
  // the body blocked independently of CI scheduling and pool initialization.
  try(var response=client.execute(request("user","slow-body",Map.of(),null),options(1500))){
   var body=response.body().asInputStream();assertEquals('d',body.read());
   assertThrows(IOException.class,body::readAllBytes);
  }finally{release.countDown();}
  assertEquals("recovered",read(client.execute(request("user","echo",Map.of(),"recovered".getBytes(StandardCharsets.UTF_8)),options(1500))));
 }
 @Test void timedOutCommitRetriedWithOriginalKeyIsWrittenOnce()throws Exception {
  var request=request("user","commit",Map.of("Idempotency-Key",List.of("original-key")),new byte[0]);
  assertThrows(IOException.class,()->client.execute(request,options(50)));assertEquals("1",read(client.execute(request,options(1000))));assertEquals(1,businessWrites.get());
 }
 @Test void nestedCallsUseReservedExecutorsRatherThanWaitingOnTheirCaller()throws Exception {
  assertEquals("nested-complete",read(client.execute(request("user","nested",Map.of("X-TJ-Call-Depth",List.of("1")),null),options(1000))));
  assertThrows(IOException.class,()->client.execute(request("user","nested",Map.of("X-TJ-Call-Depth",List.of("5")),null),options(1000)));
 }
 @Test void differentModulesCannotLeaseAllConnectionsNeededByDeeperCalls()throws Exception {
  http.close();var manager=org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.create().setMaxConnTotal(8).setMaxConnPerRoute(4).build();
  http=org.apache.hc.client5.http.impl.classic.HttpClients.custom().setConnectionManager(manager).setDefaultRequestConfig(org.apache.hc.client5.http.config.RequestConfig.custom().setConnectionRequestTimeout(org.apache.hc.core5.util.Timeout.ofMilliseconds(1000)).build()).build();
  client=limited(2);saturatedChains=true;
  var start=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(4)) {
   var futures=new ArrayList<Future<String>>();
   for(int index=0;index<4;index++){String module=index%2==0?"user":"course";futures.add(pool.submit(()->{start.await();return read(client.execute(request(module,"nested",Map.of("X-TJ-Call-Depth",List.of("1")),null),options(5000)));}));}
   start.countDown();
   try{for(var result:futures)assertEquals("nested-complete",result.get(8,TimeUnit.SECONDS));}
   catch(Exception error){serverFailures.forEach(error::addSuppressed);throw error;}
  }
 }
 @Test void boundedAdmissionRejectsExcessAndReleasesPermitAfterFailure()throws Exception {
  client=limited(1);
  try(var pool=Executors.newSingleThreadExecutor()) {
   var first=pool.submit(()->read(client.execute(request("user","blocked",Map.of(),null),options(2000))));assertTrue(arrived.await(1,TimeUnit.SECONDS));
   assertThrows(FeignException.TooManyRequests.class,()->client.execute(request("user","echo",Map.of(),new byte[0]),options(50)));
   release.countDown();assertEquals("done",first.get(3,TimeUnit.SECONDS));
   assertThrows(IOException.class,()->client.execute(request("user","slow",Map.of(),null),options(50)));
   assertEquals("recovered",read(client.execute(request("user","echo",Map.of(),"recovered".getBytes(StandardCharsets.UTF_8)),options(1000))));
  }
 }
}
