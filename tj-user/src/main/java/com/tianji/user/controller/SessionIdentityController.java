package com.tianji.user.controller;
import com.tianji.common.domain.dto.LoginUserDTO;import com.tianji.common.utils.InternalAuth;import com.tianji.common.exceptions.UnauthorizedException;
import org.springframework.web.bind.annotation.*;import org.springframework.jdbc.core.JdbcTemplate;import lombok.RequiredArgsConstructor;
@RestController @RequiredArgsConstructor @RequestMapping("/internal/v2/users")
public class SessionIdentityController {
 private final JdbcTemplate jdbc;
 @GetMapping("/{id}/session-identity") public LoginUserDTO identity(@PathVariable long id){
  InternalAuth.requireService();var rows=jdbc.queryForList("SELECT u.id,u.type,u.status,u.auth_version,d.role_id FROM `user` u LEFT JOIN user_detail d ON d.id=u.id WHERE u.id=? AND u.status=1",id);
  if(rows.isEmpty())throw new UnauthorizedException("账号已禁用或不存在");var row=rows.getFirst();long type=((Number)row.get("type")).longValue();Long role=type==2?2L:type==3?3L:row.get("role_id")==null?null:((Number)row.get("role_id")).longValue();
  if(role==null)throw new UnauthorizedException("账号角色无效");var identity=new LoginUserDTO();identity.setUserId(id);identity.setRoleId(role);identity.setAuthVersion(((Number)row.get("auth_version")).longValue());return identity;
 }
}
