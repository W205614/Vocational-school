package com.tianji.compact;
import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;import java.security.MessageDigest;import java.nio.charset.StandardCharsets;import java.util.concurrent.Semaphore;
/** Internal module paths require a service credential, including login helpers. */
public final class ModuleGuard implements Filter {
 private final Semaphore uploads=new Semaphore(2);
 public void doFilter(ServletRequest req,ServletResponse res,FilterChain chain)throws IOException,ServletException {
  var request=(HttpServletRequest)req;var response=(HttpServletResponse)res;
  String path=request.getRequestURI(),expected=System.getenv("TJ_INTERNAL_TOKEN"),given=request.getHeader("X-Internal-Token");
  if(((path.startsWith("/_modules/") && !path.matches("/_modules/[a-z]+/actuator/health(/(readiness|liveness))?")) || path.equals("/actuator/prometheus")) && (expected==null || expected.length()<32 || given==null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),given.getBytes(StandardCharsets.UTF_8)))){response.setStatus(403);return;}
  boolean upload=request.getContentType()!=null && request.getContentType().startsWith("multipart/");
  if(upload && !uploads.tryAcquire()){response.setStatus(429);response.setHeader("Retry-After","2");return;}
  try{chain.doFilter(req,res);}finally{if(upload)uploads.release();}
 }
}
