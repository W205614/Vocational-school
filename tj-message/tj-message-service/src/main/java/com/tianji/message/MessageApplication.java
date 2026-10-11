package com.tianji.message;

import com.tianji.common.utils.StartupLogger;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.Environment;

import java.net.UnknownHostException;

/**
 *
 * @author wusongsong
 * @since 2022/6/23 11:05
 * @version 1.0.0 1.0.0
 **/
@SpringBootApplication
@MapperScan("com.tianji.message.mapper")
@Slf4j
public class MessageApplication {
    public static void main(String[] args) throws UnknownHostException {
        SpringApplication app = new SpringApplicationBuilder(MessageApplication.class).build(args);
        Environment env = app.run(args).getEnvironment();
        StartupLogger.log(env, log);


    }
}