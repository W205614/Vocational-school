package com.tianji.authsdk.resource.interceptors;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
public final class MetricsTokenFilter extends OncePerRequestFilter {
 @Override protected boolean shouldNotFilter(HttpServletRequest request){return !(request.getPathInfo()==null?request.getRequestURI():request.getPathInfo()).equals("/actuator/prometheus");}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
  String configured=System.getenv("TJ_INTERNAL_TOKEN"),presented=request.getHeader("X-Internal-Token");
  if(configured==null || configured.length()<32 || presented==null || !MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8),presented.getBytes(StandardCharsets.UTF_8))){
   response.setStatus(403);response.setContentType("application/json");response.getWriter().write("{\"code\":403,\"msg\":\"Trusted scrape identity required\",\"data\":null}");return;
  }
  chain.doFilter(request,response);
 }
}
