package com.tianji.authsdk.gateway.util;
import cn.hutool.http.HttpRequest;import cn.hutool.json.JSONUtil;import com.tianji.common.domain.dto.LoginUserDTO;import com.tianji.common.exceptions.*;
import org.springframework.core.env.Environment;import org.springframework.cloud.client.discovery.DiscoveryClient;import java.util.concurrent.*;
public class SessionVerifier {
 private final Environment environment;private final DiscoveryClient discovery;private final ConcurrentHashMap<String,Long> verified=new ConcurrentHashMap<>();private final ConcurrentHashMap<String,CompletableFuture<Void>> inFlight=new ConcurrentHashMap<>();
 public SessionVerifier(Environment environment,DiscoveryClient discovery){this.environment=environment;this.discovery=discovery;}
 public void validate(LoginUserDTO user){
  if(user.getUserId()==null || user.getUserId()<=0 || user.getRoleId()==null || user.getRoleId()<=0)throw new UnauthorizedException("身份信息无效");
  String key=user.getSessionId()+":"+user.getUserId()+":"+user.getRoleId()+":"+user.getAuthVersion();Long cached=verified.get(key);if(cached!=null && cached>System.nanoTime())return;
  var mine=new CompletableFuture<Void>();var existing=inFlight.putIfAbsent(key,mine);
  if(existing!=null){try{existing.get(2800,TimeUnit.MILLISECONDS);return;}catch(ExecutionException error){if(error.getCause() instanceof CommonException known)throw known;throw new ServiceUnavailableException("身份校验暂不可用");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new ServiceUnavailableException("身份校验被中断");}catch(TimeoutException error){throw new ServiceUnavailableException("身份校验暂不可用");}}
  try{validateRemote(user);mine.complete(null);}catch(RuntimeException error){mine.completeExceptionally(error);throw error;}finally{inFlight.remove(key,mine);}
 }
 private void validateRemote(LoginUserDTO user){
  if(user.getSessionId()==null || !user.getSessionId().matches("[0-9a-f-]{36}") || user.getAuthVersion()==null)throw new UnauthorizedException("请重新登录");
  String key=user.getSessionId()+":"+user.getUserId()+":"+user.getRoleId()+":"+user.getAuthVersion();Long valid=verified.get(key);long now=System.nanoTime();if(valid!=null && valid>now)return;
  String base=environment.getProperty("tj.routes.auth");if(base==null){var instances=discovery.getInstances("auth-service");if(instances==null || instances.isEmpty())throw new ServiceUnavailableException("身份服务暂不可用");base=instances.getFirst().getUri().toString();}
  try(var response=HttpRequest.get(base.replaceAll("/$","")+"/internal/v2/sessions/"+user.getSessionId()+"/validate?user="+user.getUserId()+"&role="+user.getRoleId()+"&version="+user.getAuthVersion()).header("X-Internal-Token",System.getenv("TJ_INTERNAL_TOKEN")).timeout(2500).execute()){
   if(response.getStatus()==401 || response.getStatus()==403){verified.remove(key);throw new UnauthorizedException("登录状态已失效，请重新登录");}
   if(!response.isOk() || JSONUtil.parseObj(response.body()).getInt("code",0)!=200)throw new ServiceUnavailableException("身份校验暂不可用");
   if(verified.size()>10000)verified.entrySet().removeIf(e->e.getValue()<now);if(verified.size()<10000)verified.put(key,System.nanoTime()+TimeUnit.SECONDS.toNanos(5));
  }catch(CommonException e){throw e;}catch(Exception e){throw new ServiceUnavailableException("身份校验暂不可用");}
 }
}
