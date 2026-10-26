package com.tianji.common.autoconfigure.redisson;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import static org.junit.jupiter.api.Assertions.*;

class RedissonTlsConfigurationTest {
    @Test void managedPrimaryUsesTlsAndAclWithoutOpeningASocket() throws Exception {
        var properties=new DataRedisProperties();properties.setHost("redis.invalid");properties.setPort(6380);properties.setUsername("app");properties.setPassword("synthetic-password");properties.getSsl().setEnabled(true);
        String json=RedissonConfig.configuration(properties).toJSON();assertTrue(json.contains("rediss://redis.invalid:6380"));assertTrue(json.contains("\"username\":\"app\""));
    }
}
