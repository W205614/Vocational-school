package com.tianji.mediaworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.Map;

/** Standalone process: scans/transcodes on an isolated ephemeral disk, never the support JVM. */
@SpringBootApplication(scanBasePackages="com.tianji.media.cloud")
@EnableScheduling
public class CloudMediaWorkerApplication {
    public static void main(String[] args){
        var app=new SpringApplication(CloudMediaWorkerApplication.class);
        app.setDefaultProperties(Map.of("spring.application.name","media-worker","spring.config.name","media-worker",
                "tj.cloud-media.enabled","true","tj.cloud-media.role","worker","tj.auth.resource.enable","false",
                "server.port","8086","management.endpoints.web.exposure.include","health","management.endpoint.health.show-details","never"));
        app.run(args);
    }
}
