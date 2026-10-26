package com.tianji.message.thirdparty.ali;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real SDK constructor without sending a provider request. */
class AliSmsTransportTest {
    @Test
    void buildsAndClosesTheOfficialUnshadedTransportWithManagedHttpCore() throws Exception {
        var properties = new AliProperties();
        properties.setAccessId("offline-fixture-access-id");
        properties.setAccessSecret("offline-fixture-access-secret");
        var client = new AliSmsConfig().asyncClient(properties);
        try {
            assertNotNull(client);
            assertEquals("5.4.3", Class.forName("org.apache.hc.core5.http.HttpVersion").getPackage().getImplementationVersion());
            assertEquals("5.4.3", Class.forName("org.apache.hc.core5.http2.HttpVersionPolicy").getPackage().getImplementationVersion());
            assertThrows(ClassNotFoundException.class, () -> Class.forName("com.aliyun.shaded.org.apache.hc.core5.http.HttpVersion"));
        } finally {
            client.close();
        }
    }
}
