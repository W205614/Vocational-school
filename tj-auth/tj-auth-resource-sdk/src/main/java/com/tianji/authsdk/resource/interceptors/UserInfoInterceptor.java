package com.tianji.authsdk.resource.interceptors;
import com.tianji.auth.common.constants.JwtConstants;
import com.tianji.common.utils.UserContext;
import com.tianji.common.exceptions.UnauthorizedException;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.*;
public class UserInfoInterceptor implements HandlerInterceptor {
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
        UserContext.removeUser();
        if((request.getPathInfo()==null?request.getRequestURI():request.getPathInfo()).startsWith("/internal/") || (request.getPathInfo()==null?request.getRequestURI():request.getPathInfo()).equals("/actuator/prometheus"))com.tianji.common.utils.InternalAuth.requireService();
        String id=request.getHeader(JwtConstants.USER_HEADER);
        if(id==null) return true;
        com.tianji.common.utils.InternalAuth.requireService();
        try {
            long user=Long.parseLong(id);
            if(user<=0) throw new NumberFormatException();
            UserContext.setUser(user);
            UserContext.setSession(request.getHeader("user-session"));
            String role=request.getHeader("user-role");
            if(role!=null) UserContext.setRole(Long.valueOf(role));
            return true;
        } catch(NumberFormatException e) { UserContext.removeUser();throw new UnauthorizedException("身份信息格式错误"); }
    }
    @Override public void afterCompletion(HttpServletRequest r,HttpServletResponse s,Object h,Exception e) {UserContext.removeUser();}
}
