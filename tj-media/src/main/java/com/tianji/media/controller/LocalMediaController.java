package com.tianji.media.controller;
import com.tianji.media.storage.local.LocalObjectStore;import com.tianji.common.utils.UserContext;import com.tianji.common.autoconfigure.reliability.OperationStore;
import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;import org.springframework.http.*;import org.springframework.core.io.Resource;import org.springframework.web.multipart.MultipartFile;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import java.util.*;
@RestController @RequiredArgsConstructor @ConditionalOnProperty(name="tj.local-storage.enabled",havingValue="true")
public class LocalMediaController {
 private final LocalObjectStore store;private final OperationStore operations;
 @PostMapping("/api/v2/admin/media-upload") public ResponseEntity<?> upload(@RequestParam MultipartFile file,@RequestParam float duration,@RequestHeader("Idempotency-Key") String key)throws java.io.IOException{
  UserContext.requireAdmin();if(key==null || !key.matches("[A-Za-z0-9_.:-]{1,128}"))throw new com.tianji.common.exceptions.BadRequestException("需要有效的 Idempotency-Key");String name=file.getOriginalFilename();
  if(!Float.isFinite(duration) || duration<=0 || duration>86400 || name==null || name.length()>255 || !name.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|webm)"))throw new com.tianji.common.exceptions.BadRequestException("请提供 MP4 或 WebM 视频及有效模拟时长");
  try(var input=file.getInputStream()){var media=store.uploadMedia(name,input,file.getSize(),duration,key);
   return ResponseEntity.accepted().body(operations.submit(UserContext.requireUser(),"LOCAL_MEDIA_CREATE",key,Map.of("fileId",media.getFileId(),"filename",name,"duration",duration)));
  }
 }
 @GetMapping("/local-content/{key}") public ResponseEntity<Resource> content(@PathVariable String key,@RequestParam long expires,@RequestParam String signature){
  Resource resource=store.readSigned(key,expires,signature);MediaType type=key.endsWith(".webm")?MediaType.parseMediaType("video/webm"):key.endsWith(".mp4")?MediaType.parseMediaType("video/mp4"):MediaType.APPLICATION_OCTET_STREAM;
  return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.noStore()).body(resource);
 }
}
