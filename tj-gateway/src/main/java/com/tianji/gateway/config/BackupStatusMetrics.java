package com.tianji.gateway.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Read-only observation of the backup job's atomically replaced, numeric status file. */
@Component
public class BackupStatusMetrics {
    private record Status(long lastSuccess,long lastFailure,long running) {}
    private static final Status EMPTY=new Status(0,0,0);
    private static final Set<String> KEYS=Set.of("lastSuccess","lastFailure","running");
    private final Path file;
    private volatile Status status=EMPTY;

    public BackupStatusMetrics(MeterRegistry meters,@Value("${tj.backup.status-file:/run/ops-status/backup.properties}") String filename){
        file=Path.of(filename);
        meters.gauge("tj.backup.last.success.timestamp.seconds",this,value->value.status.lastSuccess());
        meters.gauge("tj.backup.last.failure.timestamp.seconds",this,value->value.status.lastFailure());
        meters.gauge("tj.backup.running",this,value->value.status.running());
        refresh();
    }
    @Scheduled(fixedDelay=15000)
    public void refresh(){
        try(InputStream input=Files.newInputStream(file)){
            byte[] bytes=input.readNBytes(4097);
            if(bytes.length>4096)throw new IllegalArgumentException("Backup status exceeds its size limit");
            Properties values=new Properties(){
                @Override public synchronized Object put(Object key,Object value){
                    if(containsKey(key))throw new IllegalArgumentException("Duplicate backup status key");
                    return super.put(key,value);
                }
            };
            values.load(new ByteArrayInputStream(bytes));
            if(!values.stringPropertyNames().equals(KEYS))throw new IllegalArgumentException("Incomplete backup status");
            long success=number(values,"lastSuccess"),failure=number(values,"lastFailure"),running=number(values,"running");
            if(running>1)throw new IllegalArgumentException("Invalid backup running flag");
            status=new Status(success,failure,running);
        }catch(IOException | IllegalArgumentException ignored){status=EMPTY;}
    }
    private static long number(Properties values,String key){
        String value=values.getProperty(key);
        if(value==null || !value.matches("[0-9]{1,19}"))throw new IllegalArgumentException("Backup status must contain non-negative integers");
        return Long.parseLong(value);
    }
}
