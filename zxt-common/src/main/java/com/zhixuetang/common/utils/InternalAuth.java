package com.zhixuetang.common.utils;

import com.zhixuetang.common.exceptions.ForbiddenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Service ports must remain private. Never accept this header through the public gateway. */
public final class InternalAuth {
    private InternalAuth() {}

    public static boolean trusted(jakarta.servlet.http.HttpServletRequest request) {
        if (!single(request, "X-Internal-Token") || !single(request, "X-ZXT-Service")) return false;
        return ServiceIdentity.current()
                .accepts(request.getHeader("X-ZXT-Service"), request.getHeader("X-Internal-Token"));
    }

    private static boolean single(jakarta.servlet.http.HttpServletRequest request, String name) {
        var values = request.getHeaders(name);
        return values == null || java.util.Collections.list(values).size() <= 1;
    }

    public static void requireService() {
        if (!trusted(WebUtils.getRequest())) throw new ForbiddenException("需要可信服务身份");
    }
}
