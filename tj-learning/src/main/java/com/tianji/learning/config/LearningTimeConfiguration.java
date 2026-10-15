package com.tianji.learning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class LearningTimeConfiguration {
    @Bean("learningClock") public Clock learningClock() {return Clock.system(ZoneId.of("Asia/Shanghai"));}
}
