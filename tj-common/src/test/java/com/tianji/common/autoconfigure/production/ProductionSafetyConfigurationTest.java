package com.tianji.common.autoconfigure.production;

import com.tianji.common.utils.ServiceIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ProductionSafetyConfigurationTest {
    @TempDir Path directory;
    private ServiceIdentity identity() throws Exception {
        Path file=directory.resolve("identities.json");Files.writeString(file,"{\"inbound\":{},\"outbound\":{}}");
        return new ServiceIdentity(Map.of("TJ_INTERNAL_AUTH_MODE","service-tokens","TJ_SERVICE_ID","identity","TJ_SERVICE_TOKENS_FILE",file.toString()));
    }
    private MockEnvironment environment(){var env=new MockEnvironment();env.setActiveProfiles("production");return env;}
    @Test void productionDoesNotInheritLocalIdentityOrSimulation() throws Exception {
        assertThrows(IllegalStateException.class,()->ProductionSafetyConfiguration.validate(environment(),new ServiceIdentity(Map.of("TJ_INTERNAL_TOKEN","synthetic-"+"x".repeat(40))),Map.of("TJ_PRODUCTION_TLS_MODE","mesh")));
        var env=environment().withProperty("tj.pay.simulated","true");
        assertThrows(IllegalStateException.class,()->ProductionSafetyConfiguration.validate(env,identity(),Map.of("TJ_PRODUCTION_TLS_MODE","mesh")));
    }
    @Test void configuredDependenciesRequireAuthenticatedVerifiedTransport() throws Exception {
        var env=environment().withProperty("spring.datasource.url","jdbc:mysql://db.invalid:3306/tj_user?sslMode=VERIFY_IDENTITY")
            .withProperty("spring.datasource.username","app_user").withProperty("spring.datasource.password","synthetic-db")
            .withProperty("spring.data.redis.host","redis.invalid").withProperty("spring.data.redis.username","application")
            .withProperty("spring.data.redis.password","synthetic-redis").withProperty("spring.data.redis.ssl.enabled","true");
        var identity=identity();assertDoesNotThrow(()->ProductionSafetyConfiguration.validate(env,identity,Map.of("TJ_PRODUCTION_TLS_MODE","mesh")));
        env.withProperty("spring.datasource.url","jdbc:mysql://db.invalid/tj_user?sslMode=DISABLED");
        assertThrows(IllegalStateException.class,()->ProductionSafetyConfiguration.validate(env,identity,Map.of("TJ_PRODUCTION_TLS_MODE","mesh")));
        env.withProperty("spring.datasource.url","jdbc:mysql://db.invalid/tj_user?sslMode=VERIFY_IDENTITY").withProperty("spring.data.redis.ssl.enabled","false");
        assertThrows(IllegalStateException.class,()->ProductionSafetyConfiguration.validate(env,identity,Map.of("TJ_PRODUCTION_TLS_MODE","mesh")));
    }
    @Test void localProfilesAndMissingMeshConfigurationCannotBeCalledProduction() throws Exception {
        var env=environment();env.setActiveProfiles("production","local-simulator");var identity=identity();
        assertThrows(IllegalStateException.class,()->ProductionSafetyConfiguration.validate(env,identity,Map.of("TJ_PRODUCTION_TLS_MODE","mesh")));
        env.setActiveProfiles("production");assertThrows(IllegalStateException.class,()->ProductionSafetyConfiguration.validate(env,identity,Map.of()));
    }
    @Test void profileAloneActivatesTheGuardAndAdministratorMfaCannotBeDisabled() throws Exception {
        var env=environment().withProperty("tj.production.enabled","false").withProperty("tj.production.module","auth").withProperty("tj.auth.cookie.require-secure","true");
        assertTrue(ProductionSafetyConfiguration.enabled(env));var identity=identity();
        assertThrows(IllegalStateException.class,()->ProductionSafetyConfiguration.validate(env,identity,Map.of("TJ_PRODUCTION_TLS_MODE","mesh")));
        env.withProperty("tj.auth.mfa.required","true").withProperty("TJ_MFA_ENCRYPTION_KEY",java.util.Base64.getEncoder().encodeToString(new byte[32]));
        assertDoesNotThrow(()->ProductionSafetyConfiguration.validate(env,identity,Map.of("TJ_PRODUCTION_TLS_MODE","mesh")));
    }
    @Test void duplicateOrLegacyJdbcTlsOverridesAreRejected() throws Exception {
        var env=environment().withProperty("spring.datasource.username","app").withProperty("spring.datasource.password","synthetic-db");var identity=identity();
        for(String query:java.util.List.of("sslMode=VERIFY_IDENTITY&sslMode=DISABLED","sslMode=VERIFY_IDENTITY&sslMode=VERIFY_IDENTITY","sslMode=VERIFY_IDENTITY&useSSL=false")){
            env.withProperty("spring.datasource.url","jdbc:mysql://db.invalid/tj_user?"+query);
            assertThrows(IllegalStateException.class,()->ProductionSafetyConfiguration.validate(env,identity,Map.of("TJ_PRODUCTION_TLS_MODE","mesh")));
        }
    }
}
