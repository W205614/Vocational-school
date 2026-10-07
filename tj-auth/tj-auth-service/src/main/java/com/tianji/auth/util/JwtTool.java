package com.tianji.auth.util;
import cn.hutool.json.JSONObject;import cn.hutool.jwt.*;import cn.hutool.jwt.signers.*;
import com.tianji.common.domain.dto.LoginUserDTO;import com.tianji.auth.common.constants.JwtConstants;import com.tianji.auth.service.impl.SessionStore;import com.tianji.common.exceptions.UnauthorizedException;import com.tianji.common.utils.*;
import org.springframework.stereotype.Component;import java.security.KeyPair;import java.time.*;import java.util.*;
@Component
public class JwtTool {
 private final JWTSigner signer;private final SessionStore sessions;
 public JwtTool(KeyPair pair,SessionStore sessions){this.signer=JWTSignerUtil.createSigner("rs256",pair);this.sessions=sessions;}
 private Map<String,Object> payload(LoginUserDTO user){var value=new LinkedHashMap<String,Object>();value.put("userId",user.getUserId());value.put("roleId",user.getRoleId());value.put("rememberMe",BooleanUtils.isTrue(user.getRememberMe()));value.put("sessionId",user.getSessionId());value.put("authVersion",user.getAuthVersion());return value;}
 private String token(LoginUserDTO user,String kind,String jti,Instant expiry){return JWT.create().setJWTId(jti).setPayload("iss","tianji").setPayload("aud",user.getRoleId()==2?"student":"admin").setPayload("tokenType",kind).setPayload(JwtConstants.PAYLOAD_USER_KEY,payload(user)).setExpiresAt(Date.from(expiry)).setSigner(signer).sign();}
 public String createToken(LoginUserDTO user){return token(user,"access",UUID.randomUUID().toString(),Instant.now().plus(JwtConstants.JWT_TOKEN_TTL));}
 public String createRefreshToken(LoginUserDTO user){var ttl=BooleanUtils.isTrue(user.getRememberMe())?JwtConstants.JWT_REMEMBER_ME_TTL:JwtConstants.JWT_REFRESH_TTL;var rotation=sessions.rotate(user,ttl);return token(user,"refresh",rotation.jti(),rotation.expires());}
 public LoginUserDTO parseRefreshToken(String value){
  try{
   JWT jwt=JWT.of(value).setSigner(signer);if(!jwt.verify() || !"tianji".equals(jwt.getPayload("iss")) || !"refresh".equals(jwt.getPayload("tokenType")))throw new UnauthorizedException("刷新令牌无效");JWTValidator.of(jwt).validateDate();
   var user=((JSONObject)jwt.getPayload("user")).toBean(LoginUserDTO.class);if(user.getSessionId()==null || user.getAuthVersion()==null || jwt.getPayload("jti")==null || !Objects.equals(jwt.getPayload("aud"),user.getRoleId()==2?"student":"admin"))throw new UnauthorizedException("刷新令牌无效");
   user.setRefreshJti(jwt.getPayload("jti").toString());return user;
  }catch(UnauthorizedException e){throw e;}catch(Exception e){throw new UnauthorizedException("登录状态已失效，请重新登录");}
 }
 public void cleanJtiCache(){String id=UserContext.getSession();if(id!=null)sessions.revoke(id,UserContext.requireUser());else sessions.revokeAll(UserContext.requireUser());}
}
