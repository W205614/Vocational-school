package com.tianji.media.cloud;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.TooManyRequestsException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;

/** Public artwork only: video objects and arbitrary COS keys are never addressable here. */
@Component
@ConditionalOnProperty(name="tj.cloud-media.enabled",havingValue="true")
@ConditionalOnProperty(name="tj.cloud-media.role",havingValue="api",matchIfMissing=true)
@ConditionalOnProperty(name="tj.local-storage.enabled",havingValue="false",matchIfMissing=true)
public class CloudCourseCoverStore {
    static final int MAX_BYTES=5*1024*1024;
    private final CloudObjectStore objects;
    private final Semaphore decoding=new Semaphore(2);

    public CloudCourseCoverStore(CloudObjectStore objects){this.objects=objects;}

    public String upload(InputStream input,long length)throws IOException{
        acquire();
        try{
            byte[] bytes=bounded(input,length);String suffix=validateImage(bytes);
            String key=digest(bytes)+"."+suffix;
            var info=objects.putImage("covers/"+key,bytes,contentType(key));
            // Also verifies duplicate-key responses and refuses stale/corrupt objects.
            readPinned(key,info);
            return "/api/v2/services/media/course-covers/"+key;
        }finally{decoding.release();}
    }

    public byte[] read(String key)throws IOException{
        validateKey(key);acquire();
        try{
            byte[] bytes=readPinned(key,objects.head("covers/"+key));
            if(!key.endsWith("."+validateImage(bytes)))throw new BadRequestException("封面类型不匹配");
            return bytes;
        }finally{decoding.release();}
    }

    private void acquire(){if(!decoding.tryAcquire())throw new TooManyRequestsException("图片处理繁忙，请稍后重试");}

    private byte[] readPinned(String key,CloudObjectStore.ObjectInfo info)throws IOException{
        if(info==null||!contentType(key).equals(info.contentType())||!pinned(info))throw new BadRequestException("封面对象无效");
        // Reject size before opening a network response or allocating its bounded buffer.
        if(info.size()<1||info.size()>MAX_BYTES)throw new BadRequestException("封面大小无效");
        try(var input=objects.read("covers/"+key,info.version(),info.etag(),-1,-1)){
            byte[] bytes=bounded(input,info.size());
            if(!key.substring(0,64).equals(digest(bytes)))throw new BadRequestException("封面内容校验失败");
            return bytes;
        }
    }

    private static boolean pinned(CloudObjectStore.ObjectInfo info){
        return info.version()!=null&&!info.version().isBlank()&&!info.version().equals("null")||info.etag()!=null&&!info.etag().isBlank();
    }

    private static byte[] bounded(InputStream input,long length)throws IOException{
        if(length<1||length>MAX_BYTES)throw new BadRequestException("封面大小必须在 1 字节到 5 MiB 之间");
        byte[] bytes=input.readNBytes(MAX_BYTES+1);
        if(bytes.length!=length)throw new BadRequestException("封面上传不完整或超过大小限制");
        return bytes;
    }

    private static String validateImage(byte[] bytes)throws IOException{
        try(var image=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(image);
            if(!readers.hasNext())throw new BadRequestException("请上传有效的 PNG 或 JPEG 图片");
            var reader=readers.next();
            try{
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!Set.of("png","jpeg").contains(format))throw new BadRequestException("仅支持 PNG 或 JPEG 封面");
                reader.setInput(image);int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<=0||height<=0||width>8192||height>8192||(long)width*height>20_000_000)
                    throw new BadRequestException("封面尺寸过大，请使用不超过 8192 像素、2000 万像素的图片");
                if(reader.read(0)==null)throw new BadRequestException("图片内容无效");
                return format.equals("png")?"png":"jpg";
            }finally{reader.dispose();}
        }catch(javax.imageio.IIOException error){throw new BadRequestException("图片损坏，请重新上传");}
    }

    private static void validateKey(String key){
        if(key==null||!key.matches("[0-9a-f]{64}\\.(png|jpg)"))throw new BadRequestException("封面地址无效");
    }

    static String contentType(String key){return key.endsWith(".png")?"image/png":"image/jpeg";}
    static String digest(byte[] bytes){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException error){throw new IllegalStateException(error);}
    }
}
