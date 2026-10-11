package com.tianji.pay;

import com.tianji.common.utils.StartupLogger;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.Environment;

import java.net.UnknownHostException;

@MapperScan("com.tianji.pay.mapper")
@SpringBootApplication
@Slf4j
public class PayApplication {
    public static void main(String[] args) throws UnknownHostException {
        SpringApplication app = new SpringApplicationBuilder(PayApplication.class).build(args);
        Environment env = app.run(args).getEnvironment();
        StartupLogger.log(env, log);

    }
}