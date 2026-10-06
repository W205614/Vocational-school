package com.tianji.api.config;
import feign.Capability;
import feign.Client;
import feign.FeignException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
/** Bound in-flight calls per upstream. Rejection never reaches the network; every exit releases the permit. */
public final class FeignBulkheadCapability implements Capability {
 private final int maximum;
 private final Map<String,Semaphore> upstreams=new ConcurrentHashMap<>();
 public FeignBulkheadCapability(int maximum){if(maximum<1 || maximum>200)throw new IllegalArgumentException("Feign concurrency must be 1..200");this.maximum=maximum;}
 @Override public Client enrich(Client delegate){
  return (request,options)->{
   String upstream=URI.create(request.url()).getAuthority();
   var permits=upstreams.computeIfAbsent(upstream,key->new Semaphore(maximum));
   if(!permits.tryAcquire())throw new FeignException.TooManyRequests("Upstream concurrency limit reached",request,new byte[0],Map.of());
   try{return delegate.execute(request,options);}finally{permits.release();}
  };
 }
}
