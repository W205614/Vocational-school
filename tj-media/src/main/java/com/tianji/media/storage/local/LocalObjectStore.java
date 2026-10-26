package com.tianji.media.storage.local;
import org.springframework.core.env.Environment;
import org.springframework.core.io.*;
import com.tianji.media.domain.po.Media;
import com.tianji.media.enums.FileStatus;
import com.tianji.common.utils.UserContext;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.security.*;import javax.crypto.*;import javax.crypto.spec.SecretKeySpec;import java.util.*;
import java.time.Clock;
/** Real local bytes and metadata; enabled only in an explicit simulator environment. */
public final class LocalObjectStore {
 public static final long MAX_UPLOAD_BYTES=200L*1024*1024,MAX_IMPORT_BYTES=512L*1024*1024;
 private final Path root;private final byte[] secret;private final JsonMapper json;private final MediaDurationProbe probe;private final Clock clock;private final int signatureTtl;private final java.util.concurrent.Semaphore mediaWrites;
 public LocalObjectStore(Environment env,JsonMapper json){
  this(env,json,new FfprobeMediaDurationProbe(env,json),Clock.systemUTC());
 }
 LocalObjectStore(Environment env,JsonMapper json,MediaDurationProbe probe,Clock clock){
  if(Arrays.stream(env.getActiveProfiles()).noneMatch(Set.of("acceptance","local-simulator")::contains))throw new IllegalStateException("Local storage requires explicit simulator profile");
  this.json=json;this.probe=probe;this.clock=clock;signatureTtl=env.getProperty("tj.local-storage.signature-ttl-seconds",Integer.class,600);
  if(signatureTtl<1 || signatureTtl>600)throw new IllegalArgumentException("Local signature TTL must be 1 to 600 seconds");
  int uploadConcurrency=env.getProperty("tj.local-storage.upload-concurrency",Integer.class,2);
  if(uploadConcurrency<1 || uploadConcurrency>4)throw new IllegalArgumentException("Local video upload concurrency must be 1 to 4");
  mediaWrites=new java.util.concurrent.Semaphore(uploadConcurrency,true);
  root=Path.of(env.getRequiredProperty("tj.local-storage.directory")).toAbsolutePath().normalize();
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
 /** Compatibility parameter is ignored; only the file's actual presentation duration is trusted. */
 public Media uploadMedia(String filename,InputStream input,long length,float ignoredDuration,String uploadKey){return uploadMedia(filename,input,length,uploadKey);}
 public Media uploadMedia(String filename,InputStream input,long length,String uploadKey){return saveMedia(filename,input,length,uploadKey,MAX_UPLOAD_BYTES,null);}
 /** Caller must establish a configured, read-only source root and trusted administrative identity. */
 public Media importMedia(Path source,long length,String expectedSha256,String uploadKey){
  if(expectedSha256==null || !expectedSha256.matches("[0-9a-f]{64}"))throw new com.tianji.common.exceptions.BadRequestException("需要有效的 SHA-256");
  try(InputStream input=Files.newInputStream(source,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
   return saveMedia(source.getFileName().toString(),input,length,uploadKey,MAX_IMPORT_BYTES,expectedSha256);
  }catch(IOException error){throw new com.tianji.common.exceptions.BadRequestException("无法读取指定的导入视频");}
 }
 private Media saveMedia(String filename,InputStream input,long length,String uploadKey,long maxBytes,String expectedSha256){
  if(filename==null || filename.length()>255 || !filename.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|webm)"))throw new com.tianji.common.exceptions.BadRequestException("请提供 MP4 或 WebM 视频");
  if(uploadKey==null || !uploadKey.matches("[A-Za-z0-9_.:-]{1,128}"))throw new com.tianji.common.exceptions.BadRequestException("需要有效的 Idempotency-Key");
  if(length<=0 || length>maxBytes)throw new com.tianji.common.exceptions.BadRequestException("视频大小超出允许范围");
  long user=UserContext.requireUser();
  if(!mediaWrites.tryAcquire())throw new com.tianji.common.exceptions.TooManyRequestsException("视频导入或上传繁忙，请稍后重试");
  try{
   Path temp=Files.createTempFile(root,"media-",".tmp");
   try{
    MessageDigest digest=MessageDigest.getInstance("SHA-256"),content=MessageDigest.getInstance("SHA-256");digest.update(uploadKey.getBytes(StandardCharsets.UTF_8));digest.update(Long.toString(user).getBytes(StandardCharsets.UTF_8));
    long copied=0;
    try(OutputStream output=Files.newOutputStream(temp)){
     byte[] buffer=new byte[8192];int count;
     while((count=input.read(buffer))!=-1){
      if(copied+count>length)throw new com.tianji.common.exceptions.BadRequestException("视频字节数与声明不一致");
      copied+=count;output.write(buffer,0,count);digest.update(buffer,0,count);content.update(buffer,0,count);
     }
    }
    if(copied!=length)throw new com.tianji.common.exceptions.BadRequestException("视频字节数与声明不一致");
    String actualSha256=HexFormat.of().formatHex(content.digest());
    if(expectedSha256!=null && !MessageDigest.isEqual(expectedSha256.getBytes(StandardCharsets.US_ASCII),actualSha256.getBytes(StandardCharsets.US_ASCII)))throw new com.tianji.common.exceptions.BadRequestException("导入视频的 SHA-256 不匹配");
    float duration=FfprobeMediaDurationProbe.valid(probe.seconds(temp,filename));
    String id=HexFormat.of().formatHex(digest.digest())+(filename.toLowerCase(Locale.ROOT).endsWith(".webm")?".webm":".mp4");
    Media metadata=new Media().setFileId(id).setFilename(filename).setDuration(duration).setSize(length).setStatus(FileStatus.PROCESSED).setCreater(user).setRequestId(UUID.randomUUID().toString());
    synchronized(this){
     if(Files.exists(path(id+".json"))){
      Media prior=metadata(id);
      if(Float.compare(prior.getDuration(),duration)!=0 || !Files.isRegularFile(path(id)))throw new com.tianji.common.exceptions.ConflictException("已有媒资与实际视频不一致，请使用新请求重新登记");
      return prior;
     }
     Files.move(temp,path(id),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
     Files.writeString(path(id+".json"),json.writeValueAsString(metadata),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    return metadata;
   }finally{Files.deleteIfExists(temp);}
  }catch(IOException|GeneralSecurityException e){throw new IllegalStateException("Local video upload failed",e);}finally{mediaWrites.release();}
 }
 public Media metadata(String id){try{return json.readValue(Files.readString(path(id+".json"),StandardCharsets.UTF_8),Media.class);}catch(IOException e){throw new IllegalStateException("Local object metadata missing",e);}}
 public String signedUrl(String key){path(key);long expiry=clock.millis()/1000+signatureTtl;return "/api/v2/services/media/local-content/"+key+"?expires="+expiry+"&signature="+signature(key,expiry);}
 public String signedCourseUrl(String key,long user,long course){
  path(key);if(user<=0 || course<=0)throw new IllegalArgumentException("Invalid playback entitlement identity");
  long expiry=clock.millis()/1000+signatureTtl;
  return "/api/v2/services/media/local-content/"+key+"?expires="+expiry+"&owner="+user+"&course="+course+"&signature="+courseSignature(key,expiry,user,course);
 }
 public long expiresAt(String signedUrl){
  String query=java.net.URI.create(signedUrl).getRawQuery();
  for(String pair:query.split("&"))if(pair.startsWith("expires="))return Math.multiplyExact(Long.parseLong(pair.substring(8)),1000);
  throw new IllegalArgumentException("Signed playback URL has no expiry");
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
  long now=clock.millis();
  if(expiry<=now/1000 || expiry>now/1000+signatureTtl || signature==null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),signature.getBytes(StandardCharsets.UTF_8)))throw new com.tianji.common.exceptions.ForbiddenException("播放地址签名已过期或无效");
  Resource resource=new FileSystemResource(path(key));if(!resource.exists())throw new com.tianji.common.exceptions.BadRequestException("文件不存在");return resource;
 }
 public InputStream download(String key){try{return Files.newInputStream(path(key));}catch(IOException e){throw new IllegalStateException(e);}}
 public void delete(String key){try{Files.deleteIfExists(path(key));Files.deleteIfExists(path(key+".json"));}catch(IOException e){throw new IllegalStateException("Local deletion failed",e);}}
}
