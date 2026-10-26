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
 private final org.springframework.core.env.Environment environment;
 private final com.tianji.common.utils.ServiceIdentity identity;
 public JwtSignerHolder(DiscoveryClient discovery,org.springframework.core.env.Environment environment){this(discovery,environment,com.tianji.common.utils.ServiceIdentity.current());}
 public JwtSignerHolder(DiscoveryClient discovery,org.springframework.core.env.Environment environment,com.tianji.common.utils.ServiceIdentity identity){this.discovery=discovery;this.environment=environment;this.identity=identity;}
 @Scheduled(fixedDelayString="${tj.auth.jwk-retry-ms:10000}",scheduler="jwtTaskScheduler")
 public void refresh(){
  if(jwtSigner!=null)return;
  try{
   String base=environment.getProperty("tj.routes.auth");
   if(base==null){var instances=discovery.getInstances("auth-service");if(instances==null || instances.isEmpty()){log.warn("Authentication key provider is unavailable");return;}base=instances.getFirst().getUri().toString();}
   String uri=base.replaceAll("/$","")+"/jwks";
   var request=AuthServiceHttp.get(uri,identity,3000);
   try(var response=request.execute()){
    if(!response.isOk())throw new IllegalStateException("JWK provider HTTP "+response.getStatus());
    var key=KeyUtil.generatePublicKey(AsymmetricAlgorithm.RSA_ECB_PKCS1.getValue(),SecureUtil.decode(response.body()));
    jwtSigner=JWTSignerUtil.createSigner(JwtConstants.JWT_ALGORITHM,key);
   }
  }catch(Exception error){log.warn("Authentication key unavailable; will retry: {}",error.getClass().getSimpleName());}
 }
}
