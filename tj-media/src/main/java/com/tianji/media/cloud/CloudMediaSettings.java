package com.tianji.media.cloud;

import org.springframework.core.env.Environment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** An explicit private-bucket provider; no credentials or key defaults in source. */
public record CloudMediaSettings(String role, String bucket, String region, String secretId, String secretKey,
                                 String signingKey, long maximumBytes, int uploadTtl, int playbackTtl,
                                 String scanner, String ffmpeg, Path workDirectory, int transcodeTimeout) {
    @Override public String toString(){return "CloudMediaSettings[role="+role+", bucket="+bucket+", region="+region+", credentials=REDACTED]";}
    public static CloudMediaSettings from(Environment env) {
        String prefix="tj.cloud-media.";
        if(!"ENTITLEMENT_PROXY".equals(env.getProperty(prefix+"delivery","ENTITLEMENT_PROXY")))throw new IllegalArgumentException("Cloud playback must retain per-request entitlement checks");
        var value=new CloudMediaSettings(env.getProperty(prefix+"role","api"),required(env,prefix+"bucket"),
                required(env,prefix+"region"),required(env,prefix+"secret-id"),required(env,prefix+"secret-key"),
                env.getProperty(prefix+"signing-key",""),env.getProperty(prefix+"max-upload-bytes",Long.class,209715200L),
                env.getProperty(prefix+"upload-ttl-seconds",Integer.class,900),env.getProperty(prefix+"playback-ttl-seconds",Integer.class,600),
                env.getProperty(prefix+"scanner-command","clamscan"),env.getProperty(prefix+"ffmpeg-command","ffmpeg"),
                Path.of(env.getProperty(prefix+"worker-directory","/work/media")),
                env.getProperty(prefix+"transcode-timeout-seconds",Integer.class,1800));
        value.validate();return value;
    }
    private static String required(Environment env,String key){String value=env.getRequiredProperty(key);if(value.isBlank())throw new IllegalArgumentException("Cloud media property is required: "+key);return value;}
    public void validate(){
        if(!role.equals("api")&&!role.equals("worker"))throw new IllegalArgumentException("Cloud media role must be api or worker");
        if(!bucket.matches("[a-z0-9][a-z0-9-]{1,62}-[0-9]{5,20}")||!region.matches("[a-z]{2}-[a-z]+(?:-[a-z]+)?"))throw new IllegalArgumentException("Invalid private COS bucket or region");
        if(role.equals("api")&&signingKey.getBytes(StandardCharsets.UTF_8).length<32)throw new IllegalArgumentException("Independent cloud playback key must contain at least 32 bytes");
        if(maximumBytes<1024||maximumBytes>536870912L||uploadTtl<60||uploadTtl>900||playbackTtl<5||playbackTtl>600||transcodeTimeout<30||transcodeTimeout>3600)throw new IllegalArgumentException("Cloud media resource limits are outside supported bounds");
        if(scanner.isBlank()||ffmpeg.isBlank()||scanner.indexOf('\0')>=0||ffmpeg.indexOf('\0')>=0)throw new IllegalArgumentException("Media scanner and transcoder are required");
    }
}
