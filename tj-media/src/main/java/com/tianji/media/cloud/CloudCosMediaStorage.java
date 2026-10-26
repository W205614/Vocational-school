package com.tianji.media.cloud;

import com.tianji.media.storage.*;
import com.tianji.media.domain.po.Media;
import com.tianji.common.exceptions.*;
import com.tianji.common.utils.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.io.InputStream;
import java.util.*;

public final class CloudCosMediaStorage implements IMediaStorage {
    public record Ticket(String task,long owner,long course,long expires,String signature){}
    private final JdbcTemplate jdbc;private final CloudObjectStore objects;private final CloudMediaSettings settings;
    public CloudCosMediaStorage(JdbcTemplate jdbc,CloudObjectStore objects,CloudMediaSettings settings){this.jdbc=jdbc;this.objects=objects;this.settings=settings;}
    public String courseTicket(String file,long owner,long course){if(owner<=0||course<=0)throw new ForbiddenException("课程播放身份无效");return ticket(file,owner,course);}
    private String ticket(String file,long owner,long course){String task=task(file);ready(task);long expires=Instant.now().getEpochSecond()+settings.playbackTtl();return "/api/v2/services/media/cloud-content/"+task+"?owner="+owner+"&course="+course+"&expires="+expires+"&signature="+sign(task,owner,course,expires);}
    public long expiresAt(String url){return Long.parseLong(url.substring(url.indexOf("&expires=")+9,url.indexOf("&signature=")))*1000;}
    public Map<String,Object> authorize(Ticket ticket){
        long now=Instant.now().getEpochSecond();
        if(ticket.task()==null||!ticket.task().matches("[0-9a-f-]{36}")||ticket.owner()<=0||ticket.course()<0||ticket.expires()<now||ticket.expires()>now+settings.playbackTtl()||ticket.signature()==null||!MessageDigest.isEqual(sign(ticket.task(),ticket.owner(),ticket.course(),ticket.expires()).getBytes(StandardCharsets.UTF_8),ticket.signature().getBytes(StandardCharsets.UTF_8)))throw new ForbiddenException("云视频播放地址无效或已过期");
        return ready(ticket.task());
    }
    public InputStream content(Map<String,Object> row,long first,long last){return objects.read(row.get("object_key").toString(),(String)row.get("object_version"),(String)row.get("object_etag"),first,last);}
    private Map<String,Object> ready(String task){var rows=jdbc.queryForList("SELECT u.* FROM cloud_media_upload u JOIN media m ON m.id=u.media_id AND m.deleted=0 WHERE u.id=? AND u.state='READY'",task);if(rows.isEmpty())throw new BadRequestException("视频尚未就绪或已移除");return rows.getFirst();}
    private static String task(String file){if(file==null||!file.matches("cos:[0-9a-f-]{36}"))throw new BadRequestException("媒资不是受控云对象");return file.substring(4);}
    private String sign(String task,long owner,long course,long expires){try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(settings.signingKey().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal((task+"\n"+owner+"\n"+course+"\n"+expires).getBytes(StandardCharsets.UTF_8)));}catch(java.security.GeneralSecurityException error){throw new IllegalStateException(error);}}
    @Override public String getPlaySignature(String file,Long user,Integer preview){
        long viewer=UserContext.requireUser();if(!Long.valueOf(1L).equals(UserContext.getRole())&&!Long.valueOf(3L).equals(UserContext.getRole()))throw new ForbiddenException("需要教师或管理员权限");
        if(preview!=null)throw new BadRequestException("受控云媒资暂不提供限时试看");
        var row=ready(task(file));if(!Long.valueOf(1L).equals(UserContext.getRole())&&((Number)row.get("owner_id")).longValue()!=viewer)throw new ForbiddenException("无权预览此媒资");
        return ticket(file,viewer,0);
    }
    @Override public String getUploadSignature(){throw new BadRequestException("请通过受控云上传任务上传视频");}
    @Override public MediaUploadResult uploadFile(String name,InputStream input,long size){throw new BadRequestException("云视频必须通过隔离工作进程处理");}
    @Override public List<Media> queryMediaInfos(String...files){var result=new ArrayList<Media>();for(String file:files){var row=ready(task(file));result.add(new Media().setId(((Number)row.get("media_id")).longValue()).setFileId(file).setFilename(row.get("filename").toString()).setDuration(((Number)row.get("duration_seconds")).floatValue()).setSize(((Number)row.get("output_size")).longValue()).setCreater(((Number)row.get("owner_id")).longValue()));}return result;}
    @Override public void deleteFile(String file){var rows=jdbc.queryForList("SELECT object_key,source_key FROM cloud_media_upload WHERE id=? AND state='READY'",task(file));if(rows.isEmpty())return;for(String key:List.of("object_key","source_key"))objects.delete(rows.getFirst().get(key).toString());}
    @Override public void deleteFiles(List<String> files){files.forEach(this::deleteFile);}
}
