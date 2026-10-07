package com.tianji.media.storage.local;

import com.tianji.common.exceptions.BadRequestException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Public course artwork is separate from private signed video/file storage. */
@Component
@ConditionalOnProperty(name="tj.local-storage.enabled",havingValue="true")
public class LocalCourseCoverStore {
    private final Path root;
    private final java.util.concurrent.Semaphore decoding=new java.util.concurrent.Semaphore(2);
    public LocalCourseCoverStore(Environment environment) {
        if (Arrays.stream(environment.getActiveProfiles()).noneMatch(Set.of("acceptance","local-simulator")::contains))
            throw new IllegalStateException("Local covers require an explicit simulator profile");
        root=Path.of(environment.getRequiredProperty("tj.local-storage.directory")).toAbsolutePath().normalize().resolve("course-covers");
        try { Files.createDirectories(root); } catch(IOException error) { throw new IllegalStateException(error); }
    }
    public String upload(InputStream input,long length) throws IOException {
        if(!decoding.tryAcquire())throw new com.tianji.common.exceptions.TooManyRequestsException("图片处理繁忙，请稍后重试");
        try{return uploadBounded(input,length);}finally{decoding.release();}
    }
    private String uploadBounded(InputStream input,long length) throws IOException {
        if(length<=0 || length>5*1024*1024)throw new BadRequestException("封面大小必须在 1 字节到 5 MiB 之间");
        byte[] bytes=input.readNBytes(5*1024*1024+1);
        if(bytes.length!=length)throw new BadRequestException("封面上传不完整或超过大小限制");
        String suffix;
        try(var image=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(image);
            if(!readers.hasNext())throw new BadRequestException("请上传有效的 PNG 或 JPEG 图片");
            var reader=readers.next();
            try {
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!Set.of("png","jpeg").contains(format))throw new BadRequestException("仅支持 PNG 或 JPEG 封面");
                reader.setInput(image);int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<=0 || height<=0 || width>8192 || height>8192 || (long)width*height>20_000_000)
                    throw new BadRequestException("封面尺寸过大，请使用不超过 8192 像素、2000 万像素的图片");
                if(reader.read(0)==null)throw new BadRequestException("图片内容无效");
                suffix=format.equals("png")?"png":"jpg";
            } finally { reader.dispose(); }
        } catch(javax.imageio.IIOException error) { throw new BadRequestException("图片损坏，请重新上传"); }
        String key;
        try { key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))+"."+suffix; }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        Path temp=Files.createTempFile(root,"cover-",".tmp");
        try { Files.write(temp,bytes);Files.move(temp,path(key),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        finally { Files.deleteIfExists(temp); }
        return "/api/v2/services/media/course-covers/"+key;
    }
    private Path path(String key) {
        if(key==null || !key.matches("[0-9a-f]{64}\\.(png|jpg)"))throw new BadRequestException("封面地址无效");
        return root.resolve(key);
    }
    public Resource read(String key) {
        Resource resource=new FileSystemResource(path(key));
        if(!resource.exists())throw new BadRequestException("封面不存在");
        return resource;
    }
}
