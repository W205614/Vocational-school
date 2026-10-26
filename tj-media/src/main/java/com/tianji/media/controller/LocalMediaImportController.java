package com.tianji.media.controller;

import com.tianji.common.autoconfigure.reliability.OperationStore;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.InternalAuth;
import com.tianji.common.utils.UserContext;
import com.tianji.media.storage.local.LocalObjectStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

/** Private offline import. The source directory is configured and mounted read-only by the operator. */
@RestController
@Profile("local-simulator")
@ConditionalOnProperty(name={"tj.local-import.enabled","tj.local-storage.enabled"}, havingValue="true")
public class LocalMediaImportController {
    private final LocalObjectStore store;
    private final OperationStore operations;
    private final Path root;
    public LocalMediaImportController(LocalObjectStore store, OperationStore operations, Environment env) {
        this.store=store;this.operations=operations;
        Path configured=Path.of(env.getRequiredProperty("tj.local-import.directory"));
        try {
            if(!configured.isAbsolute() || Files.isSymbolicLink(configured) || !Files.isDirectory(configured,LinkOption.NOFOLLOW_LINKS))
                throw new IllegalStateException("Local import requires a real absolute source directory");
            root=configured.toRealPath();
        } catch(IOException error) { throw new IllegalStateException("Local import source directory is unavailable"); }
    }
    public record ImportRequest(String relativePath,String sha256,Long expectedBytes) {}
    @PostMapping("/internal/v2/media/local-import")
    public ResponseEntity<?> importMedia(@RequestBody ImportRequest request,@RequestHeader("Idempotency-Key") String key) {
        InternalAuth.requireService();UserContext.requireAdmin();
        if(request==null || request.expectedBytes()==null || request.expectedBytes()<1 || request.expectedBytes()>LocalObjectStore.MAX_IMPORT_BYTES)
            throw new BadRequestException("导入视频大小必须为 1 到 512 MiB");
        Path source=resolveSource(request.relativePath());
        try { if(Files.size(source)!=request.expectedBytes())throw new BadRequestException("导入视频字节数与清单不一致"); }
        catch(IOException error) { throw new BadRequestException("无法读取指定的导入视频"); }
        var media=store.importMedia(source,request.expectedBytes(),request.sha256(),key);
        return ResponseEntity.accepted().body(operations.submit(UserContext.requireUser(),"LOCAL_MEDIA_CREATE",key,
                Map.of("fileId",media.getFileId(),"filename",media.getFilename(),"duration",media.getDuration())));
    }
    Path resolveSource(String relative) {
        if(relative==null || relative.isBlank() || relative.length()>1024 || relative.indexOf('\\')>=0 || relative.indexOf(':')>=0 || relative.indexOf('\0')>=0)
            throw new BadRequestException("导入路径必须是源目录内的相对路径");
        final Path path;
        try { path=Path.of(relative); } catch(InvalidPathException error) { throw new BadRequestException("导入路径无效"); }
        if(path.isAbsolute())throw new BadRequestException("导入路径必须是相对路径");
        Path source=root;
        for(Path part:path) {
            if(part.toString().equals("..") || part.toString().equals("."))throw new BadRequestException("导入路径不得包含目录跳转");
            source=source.resolve(part);
            if(Files.isSymbolicLink(source))throw new BadRequestException("导入路径不得包含符号链接");
        }
        try {
            Path real=source.toRealPath();
            if(!real.startsWith(root) || !Files.isRegularFile(real,LinkOption.NOFOLLOW_LINKS))throw new BadRequestException("导入路径不属于允许的视频文件");
            return real;
        } catch(IOException error) { throw new BadRequestException("指定的导入视频不存在"); }
    }
}
