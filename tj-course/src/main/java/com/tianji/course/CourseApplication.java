package com.tianji.course;

import com.tianji.common.utils.StartupLogger;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.UnknownHostException;

/**
 * 课程微服务启动器
 * @author wusongsong
 * @since 2022/7/9 19:40
 * @version 1.0.0
 **/
@SpringBootApplication
@EnableScheduling
@MapperScan("com.tianji.course.mapper")
@EnableAspectJAutoProxy
@Slf4j
public class CourseApplication {
    public static void main(String[] args) throws UnknownHostException {
        SpringApplication app = new SpringApplicationBuilder(CourseApplication.class).build(args);
        Environment env = app.run(args).getEnvironment();
        StartupLogger.log(env, log);
    }
}