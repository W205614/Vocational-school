package com.tianji.common.autoconfigure.reliability;
import com.tianji.common.utils.UserContext;
import jakarta.servlet.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.bind.annotation.*;
import com.tianji.common.exceptions.BadRequestException;
import java.sql.Statement;
import java.util.*;
/** Persist intent before privileged writes. A failed result update remains visibly IN_PROGRESS. */
public final class AdminAudit implements HandlerInterceptor {
 private final JdbcTemplate jdbc;
 public AdminAudit(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
  Long role=UserContext.getRole(),actor=UserContext.getUser();
  String path=request.getPathInfo()==null?request.getRequestURI():request.getPathInfo();
  if(actor==null || role==null || !Set.of(1L,3L).contains(role) || Set.of("GET","HEAD","OPTIONS").contains(request.getMethod()) || path.startsWith("/internal/") || path.startsWith("/actuator/"))return true;
  String requestId=Objects.toString(org.slf4j.MDC.get("requestId"),Objects.toString(request.getHeader("requestId"),UUID.randomUUID().toString()));
  var keys=new GeneratedKeyHolder();
  jdbc.update(connection->{var s=connection.prepareStatement("INSERT INTO admin_audit(actor_id,actor_role,method,object_path,request_id,result) VALUES(?,?,?,?,?,'IN_PROGRESS')",Statement.RETURN_GENERATED_KEYS);s.setLong(1,actor);s.setLong(2,role);s.setString(3,request.getMethod());s.setString(4,path.substring(0,Math.min(path.length(),512)));s.setString(5,requestId.substring(0,Math.min(requestId.length(),80)));return s;},keys);
  request.setAttribute(AdminAudit.class.getName(),Objects.requireNonNull(keys.getKey()).longValue());return true;
 }
 @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception error){
  Object id=request.getAttribute(AdminAudit.class.getName());if(id==null)return;
  int status=response.getStatus();String result=error!=null || status>=400?"FAILED":status==202?"ACCEPTED":"SUCCEEDED";
  jdbc.update("UPDATE admin_audit SET result=?,http_status=?,finished_at=NOW(3) WHERE id=? AND result='IN_PROGRESS'",result,status,id);
 }
 @RestController public static class Query {
  private final JdbcTemplate jdbc;public Query(JdbcTemplate jdbc){this.jdbc=jdbc;}
  @GetMapping("/api/v2/admin/audit") public Map<String,Object> page(@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) Long actor,@RequestParam(required=false) String result,@RequestParam(required=false) String requestId){
   UserContext.requireAdmin();if(pageNo<1 || pageNo>10000 || pageSize<1 || pageSize>100)throw new BadRequestException("分页范围无效");
   String where=" WHERE 1=1";List<Object> values=new ArrayList<>();
   if(actor!=null){where+=" AND actor_id=?";values.add(actor);}
   if(result!=null && !result.isBlank()){if(!Set.of("IN_PROGRESS","FAILED","ACCEPTED","SUCCEEDED").contains(result))throw new BadRequestException("审计结果无效");where+=" AND result=?";values.add(result);}
   if(requestId!=null && !requestId.isBlank()){where+=" AND request_id=?";values.add(requestId);}
   Long total=jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit"+where,Long.class,values.toArray());values.add(pageSize);values.add((pageNo-1)*pageSize);
   return Map.of("total",total,"list",jdbc.queryForList("SELECT * FROM admin_audit"+where+" ORDER BY id DESC LIMIT ? OFFSET ?",values.toArray()),"pageNo",pageNo,"pageSize",pageSize);
  }
 }
}
