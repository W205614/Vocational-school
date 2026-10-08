package com.tianji.compact;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class ModuleGuard implements Filter {
 private final int internalPort;
 private final String token;
 private final Semaphore external=new Semaphore(64,true),uploads=new Semaphore(2,true);
 public ModuleGuard(int internalPort){this(internalPort,System.getenv("TJ_INTERNAL_TOKEN"));}
 ModuleGuard(int internalPort,String token){this.internalPort=internalPort;this.token=token;}
 public void doFilter(ServletRequest req,ServletResponse res,FilterChain chain)throws IOException,ServletException {
  var request=(HttpServletRequest)req;var response=(HttpServletResponse)res;
  String path=request.getRequestURI(),expected=token,given=request.getHeader("X-Internal-Token");
  boolean internal=request.getLocalPort()>=internalPort && request.getLocalPort()<internalPort+4;
  boolean health=path.matches("/_modules/[a-z]+/actuator/health(/(readiness|liveness))?") || path.startsWith("/actuator/health") || path.equals("/readyz");
  if((internal || (path.startsWith("/_modules/") && !health) || path.equals("/actuator/prometheus")) && (expected==null || expected.length()<32 || given==null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),given.getBytes(StandardCharsets.UTF_8)))) {response.setStatus(403);return;}
  String depth=request.getHeader("X-TJ-Call-Depth");
  if(internal && !Integer.toString(request.getLocalPort()-internalPort+1).equals(depth)) {response.setStatus(403);return;}
  if(!internal && depth!=null) {response.setStatus(403);return;}
  boolean upload=request.getContentType()!=null && request.getContentType().startsWith("multipart/");
  boolean admitted=false,uploadAdmitted=false;
  try {
   if(!internal && !health) {admitted=external.tryAcquire(1500,TimeUnit.MILLISECONDS);if(!admitted){busy(response);return;}}
   if(upload) {uploadAdmitted=uploads.tryAcquire(1500,TimeUnit.MILLISECONDS);if(!uploadAdmitted){busy(response);return;}}
   chain.doFilter(req,res);
  }catch(InterruptedException error){Thread.currentThread().interrupt();throw new ServletException(error);}
  finally{if(admitted)external.release();if(uploadAdmitted)uploads.release();}
 }
 private void busy(HttpServletResponse response){response.setStatus(429);response.setHeader("Retry-After","2");}
}
