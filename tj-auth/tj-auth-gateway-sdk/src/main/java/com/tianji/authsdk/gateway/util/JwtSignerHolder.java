package com.tianji.authsdk.gateway.util;
import cn.hutool.crypto.KeyUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.asymmetric.AsymmetricAlgorithm;
import cn.hutool.http.HttpRequest;
import cn.hutool.jwt.signers.*;
import com.tianji.auth.common.constants.JwtConstants;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.scheduling.annotation.Scheduled;
@Slf4j
public class JwtSignerHolder {
 @Getter private volatile JWTSigner jwtSigner;
 private final DiscoveryClient discovery;
 public JwtSignerHolder(DiscoveryClient discovery){this.discovery=discovery;}
 @Scheduled(fixedDelayString="${tj.auth.jwk-retry-ms:10000}",scheduler="jwtTaskScheduler")
 public void refresh(){
  if(jwtSigner!=null)return;
  try{
   var instances=discovery.getInstances("auth-service");
   if(instances==null || instances.isEmpty()){log.warn("Authentication key provider is unavailable");return;}
   var instance=instances.getFirst();
   String uri=instance.getUri().resolve("/jwks").toString();
   try(var response=HttpRequest.get(uri).timeout(3000).execute()){
    if(!response.isOk())throw new IllegalStateException("JWK provider HTTP "+response.getStatus());
    var key=KeyUtil.generatePublicKey(AsymmetricAlgorithm.RSA_ECB_PKCS1.getValue(),SecureUtil.decode(response.body()));
    jwtSigner=JWTSignerUtil.createSigner(JwtConstants.JWT_ALGORITHM,key);
   }
  }catch(Exception error){log.warn("Authentication key unavailable; will retry: {}",error.getClass().getSimpleName());}
 }
}
