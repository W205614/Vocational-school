package com.tianji.media.config;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.vod.v20180717.VodClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** No request is sent: prove the supported explicit credential path does not
 * require the vulnerable optional INI profile parser removed from the SDK graph. */
class TencentCredentialBoundaryTest {
    @Test void explicitCredentialsInitializeWithoutIniProfileParsing() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.ini4j.Ini"));
        var credentials = new Credential("synthetic-unit-test-id", "synthetic-unit-test-key");
        assertNotNull(new VodClient(credentials, "ap-shanghai", new ClientProfile()));
    }

    @Test void genericCosClientUsesHttpsAndBoundedResourcesWithoutMakingRequests() {
        var properties = new TencentProperties();
        properties.setSecretId("synthetic-unit-test-id");
        properties.setSecretKey("synthetic-unit-test-key");
        var cos = new TencentProperties.CosProperties();
        cos.setRegion("ap-shanghai");
        properties.setCos(cos);
        var client = new TencentConfig().tencentCosClient(properties);
        try {
            var config = client.getClientConfig();
            assertEquals(com.qcloud.cos.http.HttpProtocol.https, config.getHttpProtocol());
            assertEquals(3000, config.getConnectionTimeout());
            assertEquals(3000, config.getConnectionRequestTimeout());
            assertEquals(15000, config.getSocketTimeout());
            assertEquals(16, config.getMaxConnectionsCount());
            assertEquals(1, config.getMaxErrorRetry());
        } finally {
            client.shutdown();
        }
    }
}
