package com.tianji.exam;

import com.tianji.common.utils.StartupLogger;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.Environment;

import java.net.UnknownHostException;

@Slf4j
@MapperScan("com.tianji.exam.mapper")
@SpringBootApplication
public class ExamApplication {
    public static void main(String[] args) throws UnknownHostException {
        SpringApplication app = new SpringApplicationBuilder(ExamApplication.class).build(args);
        Environment env = app.run(args).getEnvironment();
        StartupLogger.log(env, log);

    }
}
