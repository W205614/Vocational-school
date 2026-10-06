package com.tianji.auth.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.bootstrap.encrypt.KeyProperties;
import org.springframework.context.annotation.*;
import java.security.*;
@Configuration(proxyBeanMethods=false)
public class AuthConfig {
    @Bean @ConfigurationProperties(prefix="encrypt")
    public KeyProperties keyProperties() {return new KeyProperties();}
    @Bean public KeyPair keyPair(KeyProperties properties) throws Exception {
        var config=properties.getKeyStore();
        KeyStore store=KeyStore.getInstance("JKS");
        try(var input=config.getLocation().getInputStream()) {store.load(input,config.getPassword().toCharArray());}
        PrivateKey key=(PrivateKey)store.getKey(config.getAlias(),config.getSecret().toCharArray());
        if(key==null || store.getCertificate(config.getAlias())==null) throw new IllegalStateException("JWT signing key is missing");
        return new KeyPair(store.getCertificate(config.getAlias()).getPublicKey(),key);
    }
}
