package com.tianji.media.cloud;

import com.tianji.common.exceptions.BadRequestException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One bounded task per standalone worker. Leases and immutable output keys make restarts safe. */
@Component
@ConditionalOnProperty(name="tj.cloud-media.enabled",havingValue="true")
@ConditionalOnProperty(name="tj.cloud-media.role",havingValue="worker")
public class CloudMediaWorker implements ApplicationRunner {
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final CloudObjectStore objects;
    private final CloudMediaSettings settings;private final CloudMediaProcessor processor;
    private final Path signatures;private final long signatureAge;
    private volatile boolean started;
    private final java.security.SecureRandom identifiers=new java.security.SecureRandom();
    public CloudMediaWorker(JdbcTemplate jdbc,PlatformTransactionManager manager,CloudObjectStore objects,CloudMediaSettings settings,Environment env){this.jdbc=jdbc;tx=new TransactionTemplate(manager);this.objects=objects;this.settings=settings;processor=new CloudMediaProcessor(settings,env);signatures=Path.of(env.getProperty("tj.cloud-media.signature-directory","/var/lib/clamav"));signatureAge=env.getProperty("tj.cloud-media.signature-max-age-seconds",Long.class,172800L);}
    @Override public void run(ApplicationArguments args)throws java.io.IOException{objects.verifyPrivateBucket();Files.createDirectories(settings.workDirectory());started=true;}
    public boolean isStarted(){return started;}
    @Scheduled(fixedDelayString="${tj.cloud-media.poll-interval-ms:1000}") public void poll(){
        if(!started||!CloudWorkerHealth.fresh(signatures,signatureAge))return;
        jdbc.update("UPDATE cloud_media_upload SET state='FAILED',error_code='UPLOAD_EXPIRED' WHERE state='UPLOADING' AND expires_at<NOW(3)");
        var candidates=jdbc.queryForList("SELECT id FROM cloud_media_upload WHERE (state='PENDING' AND next_attempt_at<=NOW(3)) OR (state='PROCESSING' AND lease_until<NOW(3)) ORDER BY created_at LIMIT 1");
        if(!candidates.isEmpty())process(candidates.getFirst().get("id").toString());
    }
    void process(String id){
        String token=UUID.randomUUID().toString();
        if(jdbc.update("UPDATE cloud_media_upload SET state='PROCESSING',attempts=attempts+1,lease_token=?,lease_until=NOW(3)+INTERVAL 60 SECOND,error_code=NULL WHERE id=? AND attempts<3 AND ((state='PENDING' AND next_attempt_at<=NOW(3)) OR (state='PROCESSING' AND lease_until<NOW(3)))",token,id)!=1){
            jdbc.update("UPDATE cloud_media_upload SET state='FAILED',error_code='WORKER_RETRY_EXHAUSTED' WHERE id=? AND attempts>=3 AND state IN('PENDING','PROCESSING') AND (lease_until IS NULL OR lease_until<NOW(3))",id);return;
        }
        var row=jdbc.queryForMap("SELECT * FROM cloud_media_upload WHERE id=? AND lease_token=?",id,token);
        var lost=new AtomicBoolean();
        var heartbeat=Executors.newSingleThreadScheduledExecutor(r->{var thread=new Thread(r,"media-worker-lease");thread.setDaemon(true);return thread;});
        heartbeat.scheduleAtFixedRate(()->{try{if(jdbc.update("UPDATE cloud_media_upload SET lease_until=NOW(3)+INTERVAL 60 SECOND WHERE id=? AND state='PROCESSING' AND lease_token=?",id,token)!=1)lost.set(true);}catch(RuntimeException error){lost.set(true);}},10,10,TimeUnit.SECONDS);
        Runnable lease=()->{if(lost.get()||Thread.currentThread().isInterrupted())throw new IllegalStateException("MEDIA_LEASE_LOST");};
        Path directory=null;
        try{
            Files.createDirectories(settings.workDirectory());directory=Files.createTempDirectory(settings.workDirectory(),"task-");
            var source=objects.head(row.get("source_key").toString());long size=((Number)row.get("expected_size")).longValue();
            if(source.size()!=size||!"application/octet-stream".equals(source.contentType())||source.etag()==null||source.etag().isBlank())throw new BadRequestException("SOURCE_SIZE_MISMATCH");
            CloudMediaProcessor.Output output;
            try(var input=objects.read(row.get("source_key").toString(),source.version(),source.etag(),0,size-1)){
                output=processor.process(input,row.get("filename").toString(),size,row.get("expected_sha256").toString(),directory,lease);
            }
            lease.run();String objectKey="ready/"+id+"/"+token+".mp4";
            var uploaded=objects.putReady(objectKey,output.file());if(uploaded.size()!=output.size()||uploaded.etag()==null||uploaded.etag().isBlank()||!"video/mp4".equals(uploaded.contentType()))throw new IllegalStateException("OUTPUT_SIZE_MISMATCH");lease.run();
            tx.executeWithoutResult(status->{
                var owned=jdbc.queryForList("SELECT id FROM cloud_media_upload WHERE id=? AND state='PROCESSING' AND lease_token=? AND lease_until>NOW(3) FOR UPDATE",id,token);
                if(owned.isEmpty())throw new IllegalStateException("MEDIA_LEASE_LOST");
                // No pod-local Snowflake worker-id assumption; the database primary key remains authoritative.
                long mediaId=identifiers.nextLong(1,Long.MAX_VALUE);long owner=((Number)row.get("owner_id")).longValue();
                jdbc.update("INSERT INTO media(id,file_id,filename,media_url,duration,request_id,status,size,creater,updater,deleted,create_time,update_time) VALUES(?,?,?,'',?,?,3,?,?,?,0,NOW(3),NOW(3))",mediaId,"cos:"+id,row.get("filename"),output.duration(),id,output.size(),owner,owner);
                jdbc.update("UPDATE cloud_media_upload SET state='READY',object_key=?,object_version=?,object_etag=?,output_size=?,output_sha256=?,duration_seconds=?,media_id=?,lease_token=NULL,lease_until=NULL WHERE id=? AND lease_token=?",objectKey,uploaded.version(),uploaded.etag(),output.size(),output.sha256(),output.duration(),mediaId,id,token);
            });
        }catch(Exception error){
            String code=error instanceof BadRequestException?Objects.toString(error.getMessage(),"MEDIA_REJECTED"):"MEDIA_PROCESSING_FAILED";
            if(!code.matches("[A-Z_]{1,64}"))code="MEDIA_REJECTED";
            jdbc.update("UPDATE cloud_media_upload SET state='FAILED',error_code=?,lease_token=NULL,lease_until=NULL WHERE id=? AND lease_token=?",code,id,token);
        }finally{
            heartbeat.shutdownNow();
            if(directory!=null){try(var paths=Files.walk(directory)){for(Path file:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);}catch(java.io.IOException ignored){/* bounded ephemeral volume is reclaimed with the worker pod */}}
        }
    }
}
