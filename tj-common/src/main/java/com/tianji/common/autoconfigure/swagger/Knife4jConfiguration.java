package com.tianji.common.autoconfigure.swagger;

import cn.hutool.core.convert.ConverterRegistry;
import com.tianji.common.utils.TjTemporalConverter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDateTime;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SwaggerConfigProperties.class)
public class Knife4jConfiguration {
    @Bean public org.springdoc.core.customizers.PropertyCustomizer identifierSchemaCustomizer(){
        return (schema,type)->{
            if(type.getType()==Long.class || type.getType()==Long.TYPE || type.getType()==java.math.BigInteger.class){
                schema.setType("string");schema.setFormat(null);schema.setPattern("^-?[0-9]+$");
            }
            return schema;
        };
    }
    @Bean
    public OpenAPI platformOpenApi(SwaggerConfigProperties p) {
        ConverterRegistry.getInstance().putCustom(LocalDateTime.class, new TjTemporalConverter(LocalDateTime.class));
        return new OpenAPI().info(new Info().title(p.getTitle()).description(p.getDescription())
                .version(p.getVersion()).contact(new Contact().name(p.getContactName())
                        .url(p.getContactUrl()).email(p.getContactEmail())));
    }
}
