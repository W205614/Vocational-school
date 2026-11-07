package com.zhixuetang.trade;

import com.zhixuetang.common.utils.StartupLogger;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.Environment;

import java.net.UnknownHostException;

@MapperScan("com.zhixuetang.trade.mapper")
@SpringBootApplication
@Slf4j
public class TradeApplication {
    public static void main(String[] args) throws UnknownHostException {
        SpringApplication app = new SpringApplicationBuilder(TradeApplication.class).build(args);
        Environment env = app.run(args).getEnvironment();
        StartupLogger.log(env, log);
    }
}
