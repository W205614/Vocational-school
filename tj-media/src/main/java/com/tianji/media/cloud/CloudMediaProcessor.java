package com.tianji.media.cloud;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.media.storage.local.FfprobeMediaDurationProbe;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Executes only in the worker process. No shell, remote URLs, or unbounded byte buffers. */
public class CloudMediaProcessor {
    public record Output(Path file,float duration,long size,String sha256){}
    private final CloudMediaSettings settings;private final FfprobeMediaDurationProbe probe;
    public CloudMediaProcessor(CloudMediaSettings settings,Environment env){this.settings=settings;probe=new FfprobeMediaDurationProbe(env,JsonMapper.builder().build());}
    public Output process(InputStream input,String filename,long expected,String hash,Path directory,Runnable lease)throws IOException{
        Path source=directory.resolve("source"+(filename.toLowerCase(Locale.ROOT).endsWith(".webm")?".webm":".mp4")),output=directory.resolve("ready.mp4");
        copy(input,source,expected,hash,settings.maximumBytes());lease.run();
        run(List.of(settings.scanner(),"--no-summary",source.toString()),120,lease);
        float original=probe.seconds(source,source.getFileName().toString());lease.run();
        long maximumOutput=settings.maximumBytes()*2+16777216L;
        run(List.of(settings.ffmpeg(),"-nostdin","-v","error","-protocol_whitelist","file,pipe","-y","-i",source.toString(),
                "-map","0:v:0","-map","0:a:0?","-sn","-dn","-vf","scale=w='min(1920,iw)':h=-2","-c:v","libx264","-preset","veryfast","-crf","23","-threads","2",
                "-c:a","aac","-b:a","128k","-movflags","+faststart","-fs",Long.toString(maximumOutput),output.toString()),settings.transcodeTimeout(),lease);
        long size=Files.size(output);if(size<1||size>=maximumOutput)throw new BadRequestException("TRANSCODE_OUTPUT_LIMIT");
        float duration=probe.seconds(output,"ready.mp4");if(Math.abs(duration-original)>1.0f)throw new BadRequestException("TRANSCODE_DURATION_MISMATCH");
        return new Output(output,duration,size,sha256(output));
    }
    static void copy(InputStream input,Path target,long expected,String hash,long maximum)throws IOException{
        if(expected<1||expected>maximum)throw new BadRequestException("SOURCE_SIZE_LIMIT");
        MessageDigest digest=sha();long count=0;byte[] buffer=new byte[65536];long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(10);
        try(var output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){
            int read;while((read=input.read(buffer))!=-1){if(System.nanoTime()>deadline)throw new BadRequestException("SOURCE_COPY_TIMEOUT");count+=read;if(count>expected||count>maximum)throw new BadRequestException("SOURCE_SIZE_MISMATCH");digest.update(buffer,0,read);output.write(buffer,0,read);}
        }
        if(count!=expected||!MessageDigest.isEqual(HexFormat.of().formatHex(digest.digest()).getBytes(java.nio.charset.StandardCharsets.US_ASCII),hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw new BadRequestException("SOURCE_HASH_MISMATCH");
    }
    static String sha256(Path file)throws IOException{var digest=sha();try(var input=Files.newInputStream(file)){byte[] bytes=new byte[65536];int count;while((count=input.read(bytes))!=-1)digest.update(bytes,0,count);}return HexFormat.of().formatHex(digest.digest());}
    private static MessageDigest sha(){try{return MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}}
    static void run(List<String> command,int seconds,Runnable lease)throws IOException{
        Process process=new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
        try{
            while(!process.waitFor(2,TimeUnit.SECONDS)){lease.run();if(System.nanoTime()>end)throw new BadRequestException("MEDIA_TOOL_TIMEOUT");}
            if(process.exitValue()!=0)throw new BadRequestException(command.getFirst().contains("clam")?"MALWARE_SCAN_REJECTED":"MEDIA_PROCESS_REJECTED");
        }catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException("Media worker interrupted",error);}
        finally{if(process.isAlive())process.destroyForcibly();}
    }
}
