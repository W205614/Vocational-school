package com.tianji.auth.service.impl;
import com.tianji.common.domain.dto.LoginUserDTO;
import com.tianji.common.exceptions.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;import java.util.*;import java.security.*;import java.nio.charset.StandardCharsets;
@Service
public class SessionStore {
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;
 public SessionStore(JdbcTemplate jdbc,TransactionTemplate tx){this.jdbc=jdbc;this.tx=tx;}
 public record Rotation(String jti,Instant expires){}
 private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 public Rotation rotate(LoginUserDTO identity,Duration ttl){
  return tx.execute(state->{
   String next=UUID.randomUUID().toString();Instant expires=Instant.now().plus(ttl);
   if(identity.getSessionId()==null){
    identity.setSessionId(UUID.randomUUID().toString());
    jdbc.update("INSERT INTO auth_session(session_id,user_id,role_id,auth_version,audience,current_jti,rotation_jti,expires_at) VALUES(?,?,?,?,?,?,?,?)",identity.getSessionId(),identity.getUserId(),identity.getRoleId(),identity.getAuthVersion(),identity.getRoleId()==2?"student":"admin",hash(next),next,java.sql.Timestamp.from(expires));
   }else{
    var rows=jdbc.queryForList("SELECT *,expires_at>NOW(3) AS active,previous_until>=NOW(3) AS grace FROM auth_session WHERE session_id=? AND user_id=? AND revoked_at IS NULL FOR UPDATE",identity.getSessionId(),identity.getUserId());
    if(rows.isEmpty())throw new UnauthorizedException("登录状态已失效");
    var row=rows.getFirst();if(((Number)row.get("active")).intValue()!=1 || !Objects.equals(((Number)row.get("auth_version")).longValue(),identity.getAuthVersion()) || !Objects.equals(((Number)row.get("role_id")).longValue(),identity.getRoleId()))throw new UnauthorizedException("账号状态已变化，请重新登录");
    String prior=identity.getRefreshJti();if(prior==null)throw new UnauthorizedException("刷新令牌无效");
    // A concurrent request or lost response can recover the same rotation for ten seconds.
    if(hash(prior).equals(row.get("previous_jti")) && row.get("grace")!=null && ((Number)row.get("grace")).intValue()==1){
     return new Rotation(row.get("rotation_jti").toString(),jdbc.queryForObject("SELECT expires_at FROM auth_session WHERE session_id=?",(rs,n)->rs.getTimestamp(1).toInstant(),identity.getSessionId()));
    }
    if(!hash(prior).equals(row.get("current_jti")))throw new UnauthorizedException("刷新令牌已失效");
    jdbc.update("UPDATE auth_session SET previous_jti=current_jti,previous_until=NOW(3)+INTERVAL 10 SECOND,current_jti=?,rotation_jti=?,expires_at=?,last_seen_at=NOW(3) WHERE session_id=?",hash(next),next,java.sql.Timestamp.from(expires),identity.getSessionId());
   }
   return new Rotation(next,expires);
  });
 }
 public void validate(LoginUserDTO identity){
  Long count=jdbc.queryForObject("SELECT COUNT(*) FROM auth_session WHERE session_id=? AND user_id=? AND role_id=? AND auth_version=? AND revoked_at IS NULL AND expires_at>NOW(3)",Long.class,identity.getSessionId(),identity.getUserId(),identity.getRoleId(),identity.getAuthVersion());
  if(count==null || count!=1)throw new UnauthorizedException("登录状态已失效");
 }
 public void revoke(String id,long owner){jdbc.update("UPDATE auth_session SET revoked_at=NOW(3) WHERE session_id=? AND user_id=? AND revoked_at IS NULL",id,owner);}
 public void revokeAll(long owner){jdbc.update("UPDATE auth_session SET revoked_at=NOW(3) WHERE user_id=? AND revoked_at IS NULL",owner);}
 public List<Map<String,Object>> list(long owner){return jdbc.queryForList("SELECT session_id,audience,created_at,last_seen_at,expires_at FROM auth_session WHERE user_id=? AND revoked_at IS NULL AND expires_at>NOW(3) ORDER BY created_at DESC LIMIT 50",owner);}
}
