package com.tianji.common.autoconfigure.mvc;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;
@Configuration(proxyBeanMethods = false)
public class JsonConfig {
    @Bean
    public JsonMapperBuilderCustomizer platformJsonCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("platform-identifiers");
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            module.addSerializer(java.math.BigInteger.class, ToStringSerializer.instance);
            builder.addModule(module);
        };
    }
}
