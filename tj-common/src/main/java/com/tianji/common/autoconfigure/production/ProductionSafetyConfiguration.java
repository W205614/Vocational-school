package com.tianji.common.autoconfigure.production;

import com.tianji.common.utils.ServiceIdentity;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import java.util.*;

/** Production is a separate, fail-closed profile; flags are configuration checks, not HA evidence. */
@Configuration(proxyBeanMethods=false)
public class ProductionSafetyConfiguration {
    @Bean public InitializingBean productionSafety(Environment environment) {
        return ()->{if(enabled(environment))validate(environment,ServiceIdentity.current(),System.getenv());};
    }
    static boolean enabled(Environment environment){return environment.getProperty("tj.production.enabled",Boolean.class,false) || Arrays.asList(environment.getActiveProfiles()).contains("production");}

    static void validate(Environment env,ServiceIdentity identity,Map<String,String> process) {
        var profiles=Set.of(env.getActiveProfiles());
        require(profiles.contains("production") && Collections.disjoint(profiles,Set.of("local-simulator","acceptance","dev","local")),"Production requires its dedicated profile");
        require(identity.directed(),"Production requires directed service credentials");
        identity.validate();
        require("mesh".equals(process.get("TJ_PRODUCTION_TLS_MODE")),"Production requires the enforced mTLS deployment package");
        for(String name:List.of("tj.simulators.enabled","tj.pay.simulated","tj.sms.simulated","tj.local-media-import.enabled","tj.local-storage.enabled"))
            require(!env.getProperty(name,Boolean.class,false),"Simulators and local storage/import are forbidden in production");
        String jdbc=env.getProperty("spring.datasource.url");
        if(jdbc!=null) {
            require(mysqlTlsVerified(jdbc),"MySQL requires verified TLS");
            String user=env.getProperty("spring.datasource.username","");require(!user.isBlank() && !Set.of("root","admin").contains(user.toLowerCase(Locale.ROOT)),"Production requires an application database identity");
            secret(env,"spring.datasource.password");
        }
        if(env.containsProperty("spring.data.redis.host")) {
            require(env.getProperty("spring.data.redis.ssl.enabled",Boolean.class,false),"Redis requires TLS");
            secret(env,"spring.data.redis.username");secret(env,"spring.data.redis.password");
        }
        if(env.containsProperty("spring.rabbitmq.host") || env.containsProperty("spring.rabbitmq.addresses")) {
            require(env.getProperty("spring.rabbitmq.ssl.enabled",Boolean.class,false) && env.getProperty("spring.rabbitmq.ssl.verify-hostname",Boolean.class,false) && env.getProperty("spring.rabbitmq.ssl.validate-server-certificate",Boolean.class,false),"RabbitMQ requires verified TLS");
            secret(env,"spring.rabbitmq.username");secret(env,"spring.rabbitmq.password");
        }
        String elastic=env.getProperty("spring.elasticsearch.uris");
        if(elastic!=null) {
            require(Arrays.stream(elastic.split(",")).allMatch(uri->uri.trim().startsWith("https://")),"Elasticsearch requires TLS");
            secret(env,"spring.elasticsearch.username");secret(env,"spring.elasticsearch.password");
        }
        if("auth".equals(env.getProperty("tj.production.module")) || "auth-service".equals(env.getProperty("spring.application.name"))){
            require(env.getProperty("tj.auth.cookie.require-secure",Boolean.class,false),"Production refresh cookies must require Secure");
            require(env.getProperty("tj.auth.mfa.required",Boolean.class,false),"Production administrator login requires MFA");
            String key=env.getProperty("TJ_MFA_ENCRYPTION_KEY",process.getOrDefault("TJ_MFA_ENCRYPTION_KEY",""));
            try{require(Base64.getDecoder().decode(key).length==32,"MFA encryption key must contain 32 bytes");}catch(IllegalArgumentException error){throw new IllegalStateException("MFA encryption key must be base64-encoded");}
        }
        require(!env.getProperty("springdoc.api-docs.enabled",Boolean.class,false) && !env.getProperty("springdoc.swagger-ui.enabled",Boolean.class,false),"Production API documentation is private");
    }
    private static boolean mysqlTlsVerified(String jdbc){
        if(!jdbc.startsWith("jdbc:mysql://") || !jdbc.contains("?"))return false;
        int modes=0;
        try{for(String parameter:jdbc.substring(jdbc.indexOf('?')+1).split("&")){
            String[] pair=parameter.split("=",2);String name=java.net.URLDecoder.decode(pair[0],java.nio.charset.StandardCharsets.UTF_8);
            if("sslMode".equalsIgnoreCase(name)){if(pair.length!=2 || !"VERIFY_IDENTITY".equals(pair[1]))return false;modes++;}
            if(Set.of("usessl","requiressl","verifyservercertificate").contains(name.toLowerCase(Locale.ROOT)))return false;
        }}catch(IllegalArgumentException error){return false;}
        return modes==1;
    }
    private static void secret(Environment env,String name){require(!env.getProperty(name,"").isBlank(),"Missing production credential: "+name);}
    private static void require(boolean valid,String message){if(!valid)throw new IllegalStateException(message);}
}
