package com.tianji.gateway.config;
import com.tianji.authsdk.gateway.util.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.health.contributor.*;
@Configuration(proxyBeanMethods=false)
public class AuthenticationHealth {
 @Bean public HealthIndicator authenticationHealthIndicator(JwtSignerHolder signer,AuthUtil permissions){
  return ()->signer.getJwtSigner()!=null && permissions.isReady()?Health.up().build():Health.outOfService().build();
 }
}
