package com.tianji.media.cloud;

import com.tianji.common.utils.UserContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@RestController
@ConditionalOnProperty(name="tj.cloud-media.enabled",havingValue="true")
@ConditionalOnProperty(name="tj.cloud-media.role",havingValue="api",matchIfMissing=true)
@ConditionalOnProperty(name="tj.local-storage.enabled",havingValue="false",matchIfMissing=true)
public class CloudCourseCoverController {
    private final CloudCourseCoverStore covers;
    public CloudCourseCoverController(CloudCourseCoverStore covers){this.covers=covers;}

    @PostMapping("/api/v2/admin/course-cover-upload")
    public Map<String,String> upload(@RequestParam MultipartFile file)throws IOException{
        UserContext.requireAdmin();
        try(var input=file.getInputStream()){return Map.of("path",covers.upload(input,file.getSize()));}
    }

    @GetMapping("/course-covers/{key}")
    public ResponseEntity<Resource> cover(@PathVariable String key)throws IOException{
        byte[] bytes=covers.read(key);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(CloudCourseCoverStore.contentType(key)))
                .contentLength(bytes.length).header("X-Content-Type-Options","nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .body(new ByteArrayResource(bytes));
    }
}
