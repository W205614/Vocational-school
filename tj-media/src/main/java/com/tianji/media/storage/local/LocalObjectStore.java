package com.tianji.media.storage.local;
import org.springframework.core.env.Environment;
import org.springframework.core.io.*;
import com.tianji.media.domain.po.Media;
import com.tianji.media.enums.FileStatus;
import com.tianji.common.utils.UserContext;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.security.*;import javax.crypto.*;import javax.crypto.spec.SecretKeySpec;import java.util.*;
/** Real local bytes and metadata; enabled only in an explicit simulator environment. */
public final class LocalObjectStore {
 private final Path root;private final byte[] secret;private final JsonMapper json;
 public LocalObjectStore(Environment env,JsonMapper json){
  if(Arrays.stream(env.getActiveProfiles()).noneMatch(Set.of("acceptance","local-simulator")::contains))throw new IllegalStateException("Local storage requires explicit simulator profile");
  this.json=json;root=Path.of(env.getRequiredProperty("tj.local-storage.directory")).toAbsolutePath().normalize();
  String key=System.getenv("TJ_INTERNAL_TOKEN");if(key==null || key.length()<32)throw new IllegalStateException("Local signing secret required");secret=key.getBytes(StandardCharsets.UTF_8);
  try{Files.createDirectories(root);}catch(IOException e){throw new IllegalStateException(e);}
 }
 private Path path(String key){if(key==null || !key.matches("[a-zA-Z0-9_.-]{1,100}"))throw new IllegalArgumentException("Invalid object key");Path target=root.resolve(key).normalize();if(!target.getParent().equals(root))throw new IllegalArgumentException("Invalid object path");return target;}
 public String upload(String key,InputStream stream,long length){
  if(length<=0 || length>200*1024*1024)throw new IllegalArgumentException("Object size must be 1 to 200 MiB");
  Path temp=null;try{
   temp=Files.createTempFile(root,"upload-",".tmp");long copied=Files.copy(stream,temp,StandardCopyOption.REPLACE_EXISTING);
   if(copied!=length)throw new IOException("Incomplete upload");
   Files.move(temp,path(key),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);return UUID.randomUUID().toString();
  }catch(IOException e){throw new IllegalStateException("Local upload failed",e);}finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
 }
 public Media uploadMedia(String filename,InputStream input,long length,float duration,String uploadKey){
  try{
   Path temp=Files.createTempFile(root,"media-",".tmp");String id;
   try{
    if(length<=0 || length>200*1024*1024)throw new IllegalArgumentException("Video size must be 1 to 200 MiB");
    if(Files.copy(input,temp,StandardCopyOption.REPLACE_EXISTING)!=length)throw new IOException("Incomplete upload");
    MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(uploadKey.getBytes(StandardCharsets.UTF_8));digest.update(Long.toString(UserContext.requireUser()).getBytes(StandardCharsets.UTF_8));
    try(InputStream bytes=Files.newInputStream(temp)){byte[] buffer=new byte[8192];int n;while((n=bytes.read(buffer))>0)digest.update(buffer,0,n);}
    id=HexFormat.of().formatHex(digest.digest());
    String suffix=filename!=null && filename.toLowerCase(Locale.ROOT).endsWith(".webm")?".webm":".mp4";
    id+=suffix;Files.move(temp,path(id),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
   }finally{Files.deleteIfExists(temp);}
   Media metadata=new Media().setFileId(id).setFilename(filename).setDuration(duration).setSize(length).setStatus(FileStatus.PROCESSED).setCreater(UserContext.requireUser()).setRequestId(UUID.randomUUID().toString());
   // Duration is explicitly supplied by local fixtures; no claim of vendor transcoding.
   synchronized(this){
    if(Files.exists(path(id+".json"))){Media prior=metadata(id);if(Float.compare(prior.getDuration(),duration)!=0)throw new com.tianji.common.exceptions.ConflictException("相同视频的模拟时长不同");return prior;}
    Files.writeString(path(id+".json"),json.writeValueAsString(metadata),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
   }
   return metadata;
  }catch(IOException|GeneralSecurityException e){throw new IllegalStateException("Local video upload failed",e);}
 }
 public Media metadata(String id){try{return json.readValue(Files.readString(path(id+".json"),StandardCharsets.UTF_8),Media.class);}catch(IOException e){throw new IllegalStateException("Local object metadata missing",e);}}
 public String signedUrl(String key){long expiry=System.currentTimeMillis()/1000+600;return "/api/v2/services/media/local-content/"+key+"?expires="+expiry+"&signature="+signature(key,expiry);}
 public String signedCourseUrl(String key,long user,long course){
  path(key);if(user<=0 || course<=0)throw new IllegalArgumentException("Invalid playback entitlement identity");
  long expiry=System.currentTimeMillis()/1000+600;
  return "/api/v2/services/media/local-content/"+key+"?expires="+expiry+"&owner="+user+"&course="+course+"&signature="+courseSignature(key,expiry,user,course);
 }
 private String signature(String key,long expiry){return sign("v2:preview:"+key+":"+expiry);}
 private String courseSignature(String key,long expiry,long user,long course){return sign("v2:course:"+key+":"+expiry+":"+user+":"+course);}
 private String sign(String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
 public Resource readSigned(String key,long expiry,String signature){
  return readSigned(key,expiry,signature,signature(key,expiry));
 }
 public Resource readSignedCourse(String key,long expiry,String signature,long user,long course){
  if(user<=0 || course<=0)throw new com.tianji.common.exceptions.ForbiddenException("播放地址身份无效");
  return readSigned(key,expiry,signature,courseSignature(key,expiry,user,course));
 }
 private Resource readSigned(String key,long expiry,String signature,String expected){
  long now=System.currentTimeMillis()/1000;
  if(expiry<now || expiry>now+601 || signature==null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),signature.getBytes(StandardCharsets.UTF_8)))throw new com.tianji.common.exceptions.ForbiddenException("播放地址签名已过期或无效");
  Resource resource=new FileSystemResource(path(key));if(!resource.exists())throw new com.tianji.common.exceptions.BadRequestException("文件不存在");return resource;
 }
 public InputStream download(String key){try{return Files.newInputStream(path(key));}catch(IOException e){throw new IllegalStateException(e);}}
 public void delete(String key){try{Files.deleteIfExists(path(key));Files.deleteIfExists(path(key+".json"));}catch(IOException e){throw new IllegalStateException("Local deletion failed",e);}}
}
