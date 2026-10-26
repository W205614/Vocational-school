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
 private final java.util.function.BiPredicate<String,String> verifies;
 private final com.tianji.common.utils.ServiceRpcPolicy rpcPolicy;
 private final Semaphore external=new Semaphore(64,true),uploads=new Semaphore(2,true);
 public ModuleGuard(int internalPort){this(internalPort,com.tianji.common.utils.ServiceIdentity.current());}
 ModuleGuard(int internalPort,com.tianji.common.utils.ServiceIdentity identity){this.internalPort=internalPort;this.token=null;this.verifies=identity::accepts;this.rpcPolicy=identity.directed()?new com.tianji.common.utils.ServiceRpcPolicy():null;}
 ModuleGuard(int internalPort,String token){this.internalPort=internalPort;this.token=token;this.verifies=(caller,given)->token!=null && token.length()>=32 && given!=null && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),given.getBytes(StandardCharsets.UTF_8));this.rpcPolicy=null;}
 public void doFilter(ServletRequest req,ServletResponse res,FilterChain chain)throws IOException,ServletException {
  var request=(HttpServletRequest)req;var response=(HttpServletResponse)res;
  String path=request.getRequestURI(),expected=token,given=request.getHeader("X-Internal-Token");
  if(rpcPolicy!=null && !com.tianji.common.utils.ServiceRpcPolicy.canonical(path)){response.setStatus(403);return;}
  boolean internal=request.getLocalPort()>=internalPort && request.getLocalPort()<internalPort+4;
  boolean health=path.matches("/_modules/[a-z]+/actuator/health(/(readiness|liveness))?") || path.startsWith("/actuator/health") || path.equals("/readyz");
  if((internal || (path.startsWith("/_modules/") && !health) || path.equals("/actuator/prometheus")) && !verifies.test(request.getHeader("X-TJ-Service"),given)) {response.setStatus(403);return;}
  if(rpcPolicy!=null && !health && !rpcPolicy.allows(request.getHeader("X-TJ-Service"),request.getMethod(),path)){response.setStatus(403);return;}
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
