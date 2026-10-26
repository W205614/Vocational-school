package com.tianji.media.config;

import com.aliyun.oss.OSSClientBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Construct the supported explicit OSS credential path without any request. */
class AliCredentialBoundaryTest {
    @Test void explicitCredentialsInitializeWithoutIniProfileParsing() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.ini4j.Ini"));
        var client = new OSSClientBuilder().build("https://oss-cn-hangzhou.aliyuncs.com",
                "synthetic-unit-test-id", "synthetic-unit-test-key");
        try {
            assertNotNull(client);
        } finally {
            client.shutdown();
        }
    }
}
