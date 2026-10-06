package com.tianji.common.utils;
import com.tianji.common.exceptions.ForbiddenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
/** Service ports must remain private. Never accept this header through the public gateway. */
public final class InternalAuth {
    private InternalAuth() {}
    public static void requireService() {
        String configured=System.getenv("TJ_INTERNAL_TOKEN");
        String presented=WebUtils.getRequest().getHeader("X-Internal-Token");
        if(configured==null || configured.length()<32 || presented==null ||
                !MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8),presented.getBytes(StandardCharsets.UTF_8)))
            throw new ForbiddenException("需要可信服务身份");
    }
}
