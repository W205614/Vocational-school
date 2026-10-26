package com.tianji.media.cloud;

import com.tianji.common.exceptions.*;
import com.tianji.common.utils.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

public final class CloudUploadService {
    public record Create(String filename,long size,String sha256){}
    public record View(String id,String state,String filename,long size,String mediaId,Float duration,String errorCode,
                       String uploadUrl,Map<String,String> uploadHeaders,Long uploadExpiresAt){}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CloudObjectStore objects;
    private final CloudMediaSettings settings;
    public CloudUploadService(JdbcTemplate jdbc,PlatformTransactionManager manager,CloudObjectStore objects,CloudMediaSettings settings){this.jdbc=jdbc;tx=new TransactionTemplate(manager);this.objects=objects;this.settings=settings;}
    public View create(Create form,String key){
        long owner=admin();validate(form,key,settings.maximumBytes());
        String id=UUID.randomUUID().toString(),hash=digest((form.filename()+"\n"+form.size()+"\n"+form.sha256()).getBytes(StandardCharsets.UTF_8));
        return tx.execute(status->{
            jdbc.update("INSERT INTO cloud_media_upload(id,owner_id,idempotency_key,request_hash,filename,expected_size,expected_sha256,source_key,expires_at) VALUES(?,?,?,?,?,?,?,?,DATE_ADD(NOW(3),INTERVAL ? SECOND)) ON DUPLICATE KEY UPDATE id=id",id,owner,key,hash,form.filename(),form.size(),form.sha256(),"quarantine/"+id+"/source",settings.uploadTtl());
            var row=jdbc.queryForMap("SELECT *,expires_at>NOW(3) active FROM cloud_media_upload WHERE owner_id=? AND idempotency_key=? FOR UPDATE",owner,key);
            if(!hash.equals(row.get("request_hash")))throw new ConflictException("相同上传请求标识的内容已变化，请重新选择文件");
            return view(row,true);
        });
    }
    public View get(String id){return view(owned(id,admin()),true);}
    public View complete(String id){
        var row=owned(id,admin());
        if(!"UPLOADING".equals(row.get("state")))return view(row,false);
        if(!truth(row.get("active")))throw new ConflictException("上传授权已过期，请重新创建任务");
        var source=objects.head(row.get("source_key").toString());
        if(source.size()!=((Number)row.get("expected_size")).longValue()||!"application/octet-stream".equals(source.contentType()))throw new BadRequestException("上传对象的实际大小或类型与任务不匹配");
        jdbc.update("UPDATE cloud_media_upload SET state='PENDING',next_attempt_at=NOW(3),error_code=NULL WHERE id=? AND owner_id=? AND state='UPLOADING' AND expires_at>NOW(3)",id,UserContext.requireUser());
        return view(owned(id,UserContext.requireUser()),false);
    }
    public View retry(String id){
        long owner=admin();owned(id,owner);
        if(jdbc.update("UPDATE cloud_media_upload SET state='PENDING',next_attempt_at=NOW(3),error_code=NULL WHERE id=? AND owner_id=? AND state='FAILED' AND attempts<3",id,owner)!=1)throw new ConflictException("只有失败且重试次数未耗尽的任务可以重试");
        return view(owned(id,owner),false);
    }
    private Map<String,Object> owned(String id,long owner){
        if(id==null||!id.matches("[0-9a-f-]{36}"))throw new BadRequestException("上传任务编号无效");
        var rows=jdbc.queryForList("SELECT *,expires_at>NOW(3) active FROM cloud_media_upload WHERE id=? AND owner_id=?",id,owner);
        if(rows.isEmpty())throw new ForbiddenException("上传任务不存在或不属于当前账号");return rows.getFirst();
    }
    private View view(Map<String,Object> row,boolean includeUpload){
        CloudObjectStore.Upload upload=null;Long expires=null;
        if(includeUpload&&"UPLOADING".equals(row.get("state"))&&truth(row.get("active"))){
            // The database calculates this remaining interval; JVM time zones do not extend authorization.
            Long remaining=jdbc.queryForObject("SELECT GREATEST(0,TIMESTAMPDIFF(SECOND,NOW(3),expires_at)) FROM cloud_media_upload WHERE id=?",Long.class,row.get("id"));
            Instant end=Instant.now().plusSeconds(Math.min(settings.uploadTtl(),Objects.requireNonNullElse(remaining,0L)));
            upload=objects.uploadUrl(row.get("source_key").toString(),((Number)row.get("expected_size")).longValue(),end);expires=end.toEpochMilli();
        }
        return new View(row.get("id").toString(),row.get("state").toString(),row.get("filename").toString(),((Number)row.get("expected_size")).longValue(),
                row.get("media_id")==null?null:row.get("media_id").toString(),row.get("duration_seconds")==null?null:((Number)row.get("duration_seconds")).floatValue(),
                (String)row.get("error_code"),upload==null?null:upload.url(),upload==null?Map.of():upload.headers(),expires);
    }
    private long admin(){UserContext.requireAdmin();return UserContext.requireUser();}
    static boolean truth(Object value){return value instanceof Boolean b?b:value instanceof Number n&&n.intValue()!=0;}
    public static void validate(Create form,String key,long maximum){
        if(key==null||!key.matches("[A-Za-z0-9_.:-]{1,128}"))throw new BadRequestException("需要有效的 Idempotency-Key");
        if(form==null||form.filename()==null||form.filename().length()>200||form.filename().contains("/")||form.filename().contains("\\")||form.filename().indexOf('\0')>=0||!form.filename().toLowerCase(Locale.ROOT).matches(".+\\.(mp4|webm)")||form.size()<1||form.size()>maximum||form.sha256()==null||!form.sha256().matches("[0-9a-f]{64}"))throw new BadRequestException("请提供大小受限的 MP4/WebM 文件及 SHA-256");
    }
    public static String digest(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}}
}
