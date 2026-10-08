package com.tianji.api.config;
import feign.Capability;
import feign.Client;
import feign.FeignException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
/** Bound in-flight calls per upstream. Rejection never reaches the network; every exit releases the permit. */
public final class FeignBulkheadCapability implements Capability,AutoCloseable {
 private final int maximum;
 private final Set<Integer> internalPorts;
 private final Map<String,Semaphore> upstreams=new ConcurrentHashMap<>();
 private final Map<Integer,org.apache.hc.client5.http.impl.classic.CloseableHttpClient> depthPools=new java.util.HashMap<>();
 private final Map<Integer,Client> depthClients=new java.util.HashMap<>();
 private volatile boolean closed;
 public FeignBulkheadCapability(int maximum){this(maximum,Set.of());}
 public FeignBulkheadCapability(int maximum,Set<Integer> internalPorts){if(maximum<1 || maximum>200)throw new IllegalArgumentException("Feign concurrency must be 1..200");this.maximum=maximum;this.internalPorts=Set.copyOf(internalPorts);}
 @Override public Client enrich(Client delegate){
  return (request,options)->{
   if(closed)throw new IOException("HTTP admission is closed");
   URI uri=URI.create(request.url());
   Client transport=delegate;
   if(internalPorts.contains(uri.getPort())) {
    String header=request.headers().entrySet().stream().filter(e->e.getKey().equalsIgnoreCase("X-TJ-Call-Depth")).flatMap(e->e.getValue().stream()).findFirst().orElse("1");
    int depth;
    try{depth=Integer.parseInt(header);}catch(NumberFormatException e){throw new IOException("Invalid internal call depth",e);}
    if(depth<1 || depth>4)throw new IOException("Internal HTTP call depth exceeds reserved capacity");
    try {uri=new URI(uri.getScheme(),uri.getUserInfo(),uri.getHost(),uri.getPort()+depth-1,uri.getPath(),uri.getQuery(),uri.getFragment());}
    catch(java.net.URISyntaxException e){throw new IOException(e);}
    request=feign.Request.create(request.httpMethod(),uri.toASCIIString(),request.headers(),request.body(),request.charset(),request.requestTemplate());
    transport=internalClient(depth);
   }
   String path=uri.getPath();String module=path.startsWith("/_modules/")?path.split("/",4)[2]:"";
   String upstream=uri.getAuthority()+"/"+module;
   var permits=upstreams.computeIfAbsent(upstream,key->new Semaphore(maximum));
   try{if(!permits.tryAcquire(options.connectTimeoutMillis(),TimeUnit.MILLISECONDS))throw new FeignException.TooManyRequests("Upstream concurrency limit reached",request,new byte[0],Map.of());}
   catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Interrupted while waiting for HTTP admission",e);}
   try {
    var response=transport.execute(request,options);
    if(response.body()==null){permits.release();return response;}
    // Apache returns once headers arrive. Keep admission until the response body is
    // consumed/closed so a stalled decoder cannot create unbounded in-flight calls.
    var body=response.body();var closed=new java.util.concurrent.atomic.AtomicBoolean();
    return response.toBuilder().body(new feign.Response.Body(){
     public Integer length(){return body.length();}
     public boolean isRepeatable(){return body.isRepeatable();}
     public java.io.InputStream asInputStream()throws IOException {
      return new java.io.FilterInputStream(body.asInputStream()){
       @Override public void close()throws IOException{closeBody();}
      };
     }
     public java.io.Reader asReader(java.nio.charset.Charset charset)throws IOException{return new java.io.InputStreamReader(asInputStream(),charset);}
     private void closeBody()throws IOException{if(closed.compareAndSet(false,true)){try{body.close();}finally{permits.release();}}}
     public void close()throws IOException{closeBody();}
    }).build();
   }catch(IOException | RuntimeException | Error error){permits.release();throw error;}
  };
 }
 // A caller keeps its connection until its child finishes. Distinct bounded pools
 // ensure that shallow calls cannot lease all connections needed by deeper calls.
 private synchronized Client internalClient(int depth)throws IOException {
  if(closed)throw new IOException("HTTP admission is closed");
  Client current=depthClients.get(depth);if(current!=null)return current;
  int connections=Math.max(8,maximum*2);
  var manager=org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.create()
   .setMaxConnTotal(connections).setMaxConnPerRoute(connections)
   .setDefaultConnectionConfig(org.apache.hc.client5.http.config.ConnectionConfig.custom()
    .setConnectTimeout(org.apache.hc.core5.util.Timeout.ofMilliseconds(1500))
    .setSocketTimeout(org.apache.hc.core5.util.Timeout.ofMilliseconds(5000)).build()).build();
  var http=org.apache.hc.client5.http.impl.classic.HttpClients.custom().setConnectionManager(manager)
   .setDefaultRequestConfig(org.apache.hc.client5.http.config.RequestConfig.custom()
    .setConnectionRequestTimeout(org.apache.hc.core5.util.Timeout.ofMilliseconds(1500)).build()).build();
  depthPools.put(depth,http);current=new feign.hc5.ApacheHttp5Client(http);depthClients.put(depth,current);return current;
 }
 @Override public synchronized void close()throws IOException {
  if(closed)return;closed=true;IOException failure=null;
  for(var client:depthPools.values())try{client.close();}catch(IOException error){if(failure==null)failure=error;else failure.addSuppressed(error);}
  depthClients.clear();depthPools.clear();if(failure!=null)throw failure;
 }
}
