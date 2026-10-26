package com.tianji.media.cloud;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.time.Instant;

/** A scanner binary without a maintained virus database is not ready for production. */
@Component("cloudWorker")
@ConditionalOnProperty(name="tj.cloud-media.enabled",havingValue="true")
@ConditionalOnProperty(name="tj.cloud-media.role",havingValue="worker")
public class CloudWorkerHealth implements HealthIndicator {
    private final CloudMediaWorker worker;private final Path signatures;private final long maximumAge;
    public CloudWorkerHealth(CloudMediaWorker worker,Environment env){this.worker=worker;signatures=Path.of(env.getProperty("tj.cloud-media.signature-directory","/var/lib/clamav"));maximumAge=env.getProperty("tj.cloud-media.signature-max-age-seconds",Long.class,172800L);if(maximumAge<3600||maximumAge>604800)throw new IllegalArgumentException("Virus signature age must be one hour to seven days");}
    @Override public Health health(){
        if(!worker.isStarted()||!fresh(signatures,maximumAge))return Health.down().build();return Health.up().build();
    }
    static boolean fresh(Path directory,long age){
        try(var files=Files.list(directory)){
            long newest=files.filter(Files::isRegularFile).filter(file->file.getFileName().toString().matches("daily\\.(cvd|cld)")).mapToLong(file->{try{return Files.getLastModifiedTime(file).toInstant().getEpochSecond();}catch(java.io.IOException error){return 0;}}).max().orElse(0);
            long elapsed=Instant.now().getEpochSecond()-newest;return newest>0&&elapsed>=-300&&elapsed<=age;
        }catch(java.io.IOException error){return false;}
    }
}
