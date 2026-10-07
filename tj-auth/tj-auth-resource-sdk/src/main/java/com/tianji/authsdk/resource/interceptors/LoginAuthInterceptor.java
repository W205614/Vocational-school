package com.tianji.authsdk.resource.interceptors;

import com.tianji.common.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
public class LoginAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if((request.getPathInfo()==null?request.getRequestURI():request.getPathInfo()).startsWith("/internal/") || (request.getPathInfo()==null?request.getRequestURI():request.getPathInfo()).equals("/actuator/prometheus")) {
            com.tianji.common.utils.InternalAuth.requireService();
            return true;
        }
        // 1.尝试获取用户信息
        Long userId = UserContext.getUser();
        // 2.判断是否登录
        if (userId == null) {
            throw new com.tianji.common.exceptions.UnauthorizedException("未登录用户无法访问");
        }
        // 3.登录则放行
        return true;
    }
}
