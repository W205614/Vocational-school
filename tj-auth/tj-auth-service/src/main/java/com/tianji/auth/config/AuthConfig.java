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

    @Bean public com.tianji.auth.common.util.JwtVerificationKeys jwtVerificationKeys(
            KeyPair current, KeyProperties properties, org.springframework.core.env.Environment environment) throws Exception {
        var keys = new java.util.ArrayList<PublicKey>();
        keys.add(current.getPublic());
        String aliases = environment.getProperty("tj.auth.jwt.verification-aliases", "");
        if (!aliases.isBlank()) {
            var config = properties.getKeyStore();
            KeyStore store = KeyStore.getInstance("JKS");
            try (var input = config.getLocation().getInputStream()) { store.load(input, config.getPassword().toCharArray()); }
            for (String alias : aliases.split(",", -1)) {
                var certificate = store.getCertificate(alias.trim());
                if (certificate == null) throw new IllegalStateException("JWT verification certificate is missing");
                keys.add(certificate.getPublicKey());
            }
        }
        return new com.tianji.auth.common.util.JwtVerificationKeys(keys);
    }
}
