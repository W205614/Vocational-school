package com.tianji.authsdk.gateway.util;
import cn.hutool.crypto.KeyUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.asymmetric.AsymmetricAlgorithm;
import cn.hutool.jwt.signers.*;
import cn.hutool.jwt.JWT;
import com.tianji.auth.common.util.JwtVerificationKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.scheduling.annotation.Scheduled;
@Slf4j
public class JwtSignerHolder {
 private record Snapshot(JwtVerificationKeys keys,long refreshedAt) {}
 private volatile Snapshot snapshot;
 private final DiscoveryClient discovery;
 private final org.springframework.core.env.Environment environment;
 private final com.tianji.common.utils.ServiceIdentity identity;
 private final java.util.function.LongSupplier clock;
 private final long maximumAge;
 public JwtSignerHolder(DiscoveryClient discovery,org.springframework.core.env.Environment environment){this(discovery,environment,com.tianji.common.utils.ServiceIdentity.current());}
 public JwtSignerHolder(DiscoveryClient discovery,org.springframework.core.env.Environment environment,com.tianji.common.utils.ServiceIdentity identity){this(discovery,environment,identity,System::nanoTime);}
 JwtSignerHolder(DiscoveryClient discovery,org.springframework.core.env.Environment environment,com.tianji.common.utils.ServiceIdentity identity,java.util.function.LongSupplier clock){
  this.discovery=discovery;this.environment=environment;this.identity=identity;this.clock=clock;
  int seconds=environment.getProperty("tj.auth.jwk-max-age-seconds",Integer.class,60);
  if(seconds<10 || seconds>300)throw new IllegalArgumentException("JWT public-key cache age must be 10 to 300 seconds");
  long interval=environment.getProperty("tj.auth.jwk-retry-ms",Long.class,10000L);
  if(interval<1 || seconds*1000L<=interval+3000L)throw new IllegalArgumentException("JWT public-key cache age must exceed its refresh interval and 3-second HTTP timeout");
  maximumAge=java.util.concurrent.TimeUnit.SECONDS.toNanos(seconds);
 }
 private boolean fresh(Snapshot current){return current!=null && clock.getAsLong()-current.refreshedAt()<maximumAge;}
 public JWTSigner getJwtSigner(){Snapshot current=snapshot;return fresh(current)?current.keys().first():null;}
 public boolean verify(JWT jwt){
  Snapshot current=snapshot;
  if(!fresh(current))throw new com.tianji.common.exceptions.ServiceUnavailableException("鉴权密钥尚未就绪或已过期，请稍后重试");
  return current.keys().verify(jwt);
 }
 @Scheduled(fixedDelayString="${tj.auth.jwk-retry-ms:10000}",scheduler="jwtTaskScheduler")
 public void refresh(){
  try{
   String base=environment.getProperty("tj.routes.auth");
   if(base==null){var instances=discovery.getInstances("auth-service");if(instances==null || instances.isEmpty()){log.warn("Authentication key provider is unavailable");return;}base=instances.getFirst().getUri().toString();}
   String uri=base.replaceAll("/$","")+"/jwks?format=jwks";
   var request=AuthServiceHttp.get(uri,identity,3000);
   try(var response=request.execute()){
    if(!response.isOk())throw new IllegalStateException("JWK provider HTTP "+response.getStatus());
    String body=response.body();
    if(body.length()>32768)throw new IllegalArgumentException("JWT key response is too large");
    // Old identity instances ignore the optional format query during a rolling upgrade.
    var keys=body.stripLeading().startsWith("{")?JwtVerificationKeys.parse(body):new JwtVerificationKeys(java.util.List.of(
        KeyUtil.generatePublicKey(AsymmetricAlgorithm.RSA_ECB_PKCS1.getValue(),SecureUtil.decode(body))));
    snapshot=new Snapshot(keys,clock.getAsLong());
   }
  }catch(Exception error){log.warn("Authentication key unavailable; will retry: {}",error.getClass().getSimpleName());}
 }
}
