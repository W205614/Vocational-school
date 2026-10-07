package com.tianji.auth.controller;
import com.tianji.auth.service.impl.SessionStore;import com.tianji.api.client.user.UserClient;import com.tianji.common.domain.dto.LoginUserDTO;import com.tianji.common.utils.*;import com.tianji.common.exceptions.UnauthorizedException;
import org.springframework.web.bind.annotation.*;import lombok.RequiredArgsConstructor;import java.util.*;
@RestController @RequiredArgsConstructor
public class SessionController {
 private final SessionStore sessions;private final UserClient users;
 @GetMapping("/accounts/sessions") public Object list(){return sessions.list(UserContext.requireUser());}
 @DeleteMapping("/accounts/sessions/{id}") public void revoke(@PathVariable String id){sessions.revoke(id,UserContext.requireUser());}
 @GetMapping("/internal/v2/sessions/{id}/validate") public com.tianji.common.domain.R<?> validate(@PathVariable String id,@RequestParam long user,@RequestParam long role,@RequestParam long version){
  InternalAuth.requireService();var current=users.sessionIdentity(user);
  if(current==null || !Objects.equals(current.getRoleId(),role) || !Objects.equals(current.getAuthVersion(),version))throw new UnauthorizedException("账号身份已变化");current.setSessionId(id);sessions.validate(current);return com.tianji.common.domain.R.ok();
 }
}
