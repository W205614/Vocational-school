package com.tianji.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeCapabilitiesTest {
    @Test void simulatorKeepsLocalMediaAndReportsUndeliveredCloudCapabilities(){
        var env=new MockEnvironment().withProperty("tj.simulators.enabled","true").withProperty("tj.simulators.storage","true").withProperty("tj.simulators.video","true");
        var capabilities=new RuntimeCapabilities(env);assertDoesNotThrow(capabilities::afterPropertiesSet);
        var data=(Map<?,?>)new SimulatorRoutes.EnvironmentController(env,capabilities).status().getData();
        assertEquals("SIMULATED",data.get("mode"));
        assertEquals(Map.of("localUpload",true,"localPlayback",true,"cloudUpload",false,"cloudPlayback",false),data.get("capabilities"));
    }
    @Test void realModeCannotStartByClaimingVendorCredentialsAreConfigured(){
        var capabilities=new RuntimeCapabilities(new MockEnvironment().withProperty("tj.thirdparty.video.configured","true").withProperty("tj.thirdparty.storage.configured","true"));
        var error=assertThrows(IllegalStateException.class,capabilities::afterPropertiesSet);assertTrue(error.getMessage().contains("cloud video player"));
        assertFalse(capabilities.media().get("cloudPlayback"));
    }
    @Test void deliveredPrivateCosAdapterEnablesNativeCloudPlaybackOnlyWithTheProductionProfile(){
        var env=new MockEnvironment().withProperty("tj.production.enabled","true").withProperty("tj.cloud-media.enabled","true")
            .withProperty("tj.platform.media","CLOUD_COS").withProperty("tj.cloud-media.delivery","ENTITLEMENT_PROXY");
        env.setActiveProfiles("production");var capabilities=new RuntimeCapabilities(env);
        assertDoesNotThrow(capabilities::afterPropertiesSet);assertTrue(capabilities.media().get("cloudPlayback"));assertFalse(capabilities.media().get("localPlayback"));
        env.setActiveProfiles("local-simulator");assertThrows(IllegalStateException.class,capabilities::afterPropertiesSet);
    }
}
