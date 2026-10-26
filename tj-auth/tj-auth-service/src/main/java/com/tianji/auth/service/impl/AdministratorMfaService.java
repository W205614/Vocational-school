package com.tianji.auth.service.impl;

import com.tianji.common.domain.dto.LoginUserDTO;
import com.tianji.common.exceptions.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class AdministratorMfaService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final SessionStore sessions;
    private final TotpSecrets secrets;
    private final boolean required;
    public AdministratorMfaService(JdbcTemplate jdbc,TransactionTemplate tx,SessionStore sessions,Environment env){
        this.jdbc=jdbc;this.tx=tx;this.sessions=sessions;
        required=env.getProperty("tj.auth.mfa.required",Boolean.class,false);
        String key=env.getProperty("tj.auth.mfa.encryption-key",env.getProperty("TJ_MFA_ENCRYPTION_KEY",""));
        if(required && key.isBlank())throw new IllegalStateException("Production administrator MFA requires an external encryption key");
        secrets=key.isBlank()?null:new TotpSecrets(key);
    }
    public record Enrollment(String secret,String provisioningUri) {
        @Override public String toString(){return "Enrollment[secret=REDACTED,provisioningUri=REDACTED]";}
    }
    public Enrollment enroll(long user){
        configured();
        return tx.execute(status->{
            // INSERT + FOR UPDATE serialize concurrent first enrollment without disclosing an existing secret.
            String secret=secrets.generate();
            jdbc.update("INSERT INTO auth_mfa(user_id,secret_ciphertext,pending_until) VALUES(?,?,NOW(3)+INTERVAL 10 MINUTE) ON DUPLICATE KEY UPDATE user_id=user_id",user,secrets.encrypt(user,secret));
            var row=jdbc.queryForMap("SELECT enabled FROM auth_mfa WHERE user_id=? FOR UPDATE",user);
            if(truth(row.get("enabled")))throw new ForbiddenException("验证器已启用；变更或遗失请联系安全管理员处理");
            jdbc.update("UPDATE auth_mfa SET secret_ciphertext=?,pending_until=NOW(3)+INTERVAL 10 MINUTE,last_step=-1 WHERE user_id=? AND enabled=0",secrets.encrypt(user,secret),user);
            return new Enrollment(secret,"otpauth://totp/"+URLEncoder.encode("Tianji:"+user,StandardCharsets.UTF_8)+"?secret="+secret+"&issuer=Tianji&algorithm=SHA1&digits=6&period=30");
        });
    }
    public void confirm(long user,String code){
        configured();
        tx.executeWithoutResult(status->{
            var rows=jdbc.queryForList("SELECT *,UNIX_TIMESTAMP(NOW()) AS epoch,pending_until>NOW(3) AS active FROM auth_mfa WHERE user_id=? FOR UPDATE",user);
            if(rows.isEmpty() || truth(rows.getFirst().get("enabled")) || !truth(rows.getFirst().get("active")))throw new BadRequestException("验证器设置已过期，请重新开始");
            var row=rows.getFirst();long step=TotpSecrets.matchingStep(secrets.decrypt(user,row.get("secret_ciphertext").toString()),code,((Number)row.get("epoch")).longValue(),-1);
            if(step<0)throw new BadRequestException("验证器验证码无效");
            jdbc.update("UPDATE auth_mfa SET enabled=1,pending_until=NULL,last_step=? WHERE user_id=?",step,user);sessions.revokeAll(user);
        });
    }
    public boolean verifyLogin(LoginUserDTO user,String code){
        if(!Long.valueOf(1).equals(user.getRoleId()))return false;
        // Existing local installations need no new table access until MFA is configured.
        if(secrets==null && !required)return false;
        return Boolean.TRUE.equals(tx.execute(status->{
            var rows=jdbc.queryForList("SELECT *,UNIX_TIMESTAMP(NOW()) AS epoch FROM auth_mfa WHERE user_id=? FOR UPDATE",user.getUserId());
            if(rows.isEmpty() || !truth(rows.getFirst().get("enabled"))){if(required)throw new ForbiddenException("管理员必须先设置验证器，再使用验证码登录");return false;}
            var row=rows.getFirst();long step=TotpSecrets.matchingStep(secrets.decrypt(user.getUserId(),row.get("secret_ciphertext").toString()),code,((Number)row.get("epoch")).longValue(),((Number)row.get("last_step")).longValue());
            if(step<0)throw new UnauthorizedException("验证器验证码无效、过期或已使用");
            jdbc.update("UPDATE auth_mfa SET last_step=? WHERE user_id=?",step,user.getUserId());
            return true;
        }));
    }
    public void markSession(LoginUserDTO user){jdbc.update("UPDATE auth_session SET mfa_verified=1 WHERE session_id=? AND user_id=?",user.getSessionId(),user.getUserId());}
    public boolean isRequired(){return required;}
    private void configured(){if(secrets==null)throw new ServiceUnavailableException("验证器暂未配置，请联系管理员");}
    private static boolean truth(Object value){return value instanceof Boolean b?b:value instanceof Number n&&n.intValue()!=0;}
}
