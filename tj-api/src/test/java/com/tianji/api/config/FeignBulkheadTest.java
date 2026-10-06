package com.tianji.api.config;
import feign.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class FeignBulkheadTest {
 private Request request(String host){return Request.create(Request.HttpMethod.GET,"http://"+host+"/test",Map.of(),null,StandardCharsets.UTF_8,null);}
 @Test void stalledUpstreamCannotConsumeAnotherUpstreamOrExceedItsLimit() throws Exception {
  var entered=new CountDownLatch(2);var release=new CountDownLatch(1);var capability=new FeignBulkheadCapability(2);
  Client client=capability.enrich((Client)(request,options)->{
   if(request.url().contains("stalled")){entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("test deadline");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}
   return Response.builder().request(request).status(200).headers(Map.of()).build();
  });
  try(var pool=Executors.newFixedThreadPool(2)){
   var first=pool.submit(()->client.execute(request("stalled"),new Request.Options()));var second=pool.submit(()->client.execute(request("stalled"),new Request.Options()));
   try {
    assertTrue(entered.await(3,TimeUnit.SECONDS));
    assertEquals(429,assertThrows(FeignException.TooManyRequests.class,()->client.execute(request("stalled"),new Request.Options())).status());
    assertEquals(200,client.execute(request("healthy"),new Request.Options()).status());
   }finally{release.countDown();}
   first.get(5,TimeUnit.SECONDS);second.get(5,TimeUnit.SECONDS);
   assertEquals(200,client.execute(request("stalled"),new Request.Options()).status());
  }
 }
 @Test void failedNetworkCallsReleaseTheirPermits(){
  var capability=new FeignBulkheadCapability(1);Client failure=capability.enrich((Client)(request,options)->{throw new IOException("injected");});
  for(int i=0;i<10;i++)assertThrows(IOException.class,()->failure.execute(request("service"),new Request.Options()));
  Client success=capability.enrich((Client)(request,options)->Response.builder().request(request).status(200).headers(Map.of()).build());
  assertDoesNotThrow(()->success.execute(request("service"),new Request.Options()));
 }
}
