package com.tianji.gateway;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.naming.NamingService;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class NacosSdkRuntimeTest {
    @Test
    void pureSdkInitializesConfigAndDiscoveryWithManagedDependencies() throws Exception {
        assertTrue(NacosFactory.class.getProtectionDomain().getCodeSource().getLocation().toString()
                .contains("nacos-api-"));
        assertTrue(Class.forName("com.alibaba.nacos.client.config.NacosConfigService")
                .getProtectionDomain().getCodeSource().getLocation().toString().endsWith("-pure.jar"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.alibaba.nacos.shaded.com.google.common.collect.CompactHashMap"));

        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:1");
        // Only SDK initialization and dependency linkage are under test; no registry is required.
        ConfigService config = NacosFactory.createConfigService(properties);
        try {
            NamingService naming = NacosFactory.createNamingService(properties);
            try {
                assertEquals("DOWN", config.getServerStatus());
                assertEquals("DOWN", naming.getServerStatus());
            } finally {
                naming.shutDown();
            }
        } finally {
            config.shutDown();
        }
    }
}
