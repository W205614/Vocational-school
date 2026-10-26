package com.tianji.media.cloud;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name="tj.cloud-media.enabled",havingValue="true")
@ConditionalOnProperty(name="tj.cloud-media.role",havingValue="api",matchIfMissing=true)
public class CloudUploadController {
    private final CloudUploadService uploads;
    public CloudUploadController(CloudUploadService uploads){this.uploads=uploads;}
    @PostMapping("/api/v2/admin/media-upload-tasks") public ResponseEntity<?> create(@RequestBody CloudUploadService.Create form,@RequestHeader("Idempotency-Key") String key){return ResponseEntity.accepted().body(uploads.create(form,key));}
    @GetMapping("/api/v2/admin/media-upload-tasks/{id}") public CloudUploadService.View get(@PathVariable String id){return uploads.get(id);}
    @PostMapping("/api/v2/admin/media-upload-tasks/{id}/complete") public ResponseEntity<?> complete(@PathVariable String id){return ResponseEntity.accepted().body(uploads.complete(id));}
    @PostMapping("/api/v2/admin/media-upload-tasks/{id}/retry") public ResponseEntity<?> retry(@PathVariable String id){return ResponseEntity.accepted().body(uploads.retry(id));}
}
