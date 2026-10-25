package com.tianji.user.service.impl;

import com.tianji.api.dto.user.InitialPasswordDTO;
import com.tianji.api.dto.user.TemporaryCredentialDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.user.domain.po.User;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

@Service
public class TemporaryCredentialService implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final TransactionTemplate tx;
    private final SecureRandom random=new SecureRandom();
    public TemporaryCredentialService(JdbcTemplate jdbc,PasswordEncoder passwords,PlatformTransactionManager manager){
        this.jdbc=jdbc;this.passwords=passwords;this.tx=new TransactionTemplate(manager);
    }
    private String generate(){byte[] bytes=new byte[24];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    public String initialize(User user){
        String raw=generate();
        user.setPassword(passwords.encode(raw));user.setMustChangePassword(true);
        user.setTemporaryPasswordExpiresAt(jdbc.queryForObject("SELECT DATE_ADD(NOW(3),INTERVAL 24 HOUR)",LocalDateTime.class));
        return raw;
    }
    public void requireActive(User user){
        LocalDateTime now=jdbc.queryForObject("SELECT NOW(3)",LocalDateTime.class);
        if(user.getTemporaryPasswordExpiresAt()==null || !user.getTemporaryPasswordExpiresAt().isAfter(now))throw new BadRequestException("临时密码已过期或需要重新签发，请联系管理员重置");
    }
    public TemporaryCredentialDTO receipt(User user,String raw){
        return new TemporaryCredentialDTO(user.getId().toString(),user.getUsername(),raw,user.getTemporaryPasswordExpiresAt());
    }
    public TemporaryCredentialDTO reset(long userId){
        return tx.execute(status->{
            var rows=jdbc.queryForList("SELECT id,username FROM `user` WHERE id=? FOR UPDATE",userId);
            if(rows.isEmpty())throw new BadRequestException("用户不存在");
            String raw=generate();
            jdbc.update("UPDATE `user` SET password=?,must_change_password=1,temporary_password_expires_at=DATE_ADD(NOW(3),INTERVAL 24 HOUR) WHERE id=?",passwords.encode(raw),userId);
            LocalDateTime expires=jdbc.queryForObject("SELECT temporary_password_expires_at FROM `user` WHERE id=?",LocalDateTime.class,userId);
            return new TemporaryCredentialDTO(Long.toString(userId),rows.getFirst().get("username").toString(),raw,expires);
        });
    }
    public void complete(InitialPasswordDTO form){
        if(form.username()==null || form.oldPassword()==null || form.newPassword()==null || !("student".equals(form.audience()) || "admin".equals(form.audience())))throw new BadRequestException("改密信息无效");
        if(form.newPassword().length()<8 || form.newPassword().length()>72 || form.newPassword().getBytes(StandardCharsets.UTF_8).length>72 || form.newPassword().equals(form.oldPassword()))throw new BadRequestException("新密码须为8至72字符且不能与临时密码相同（UTF-8最多72字节）");
        tx.executeWithoutResult(status->{
            var rows=jdbc.queryForList("SELECT id,password,type,status,must_change_password,temporary_password_expires_at>NOW(3) AS active FROM `user` WHERE username=? FOR UPDATE",form.username());
            if(rows.size()!=1)throw new BadRequestException("临时密码无效或已过期，请联系管理员重置");
            var row=rows.getFirst();boolean student=((Number)row.get("type")).intValue()==2;
            if(((Number)row.get("status")).intValue()!=1 || student!=form.audience().equals("student") || !truth(row.get("must_change_password")) || !truth(row.get("active")) || !passwords.matches(form.oldPassword(),row.get("password").toString()))throw new BadRequestException("临时密码无效或已过期，请联系管理员重置");
            jdbc.update("UPDATE `user` SET password=?,must_change_password=0,temporary_password_expires_at=NULL WHERE id=?",passwords.encode(form.newPassword()),row.get("id"));
        });
    }
    private static boolean truth(Object value){return value instanceof Boolean b?b:value instanceof Number n&&n.intValue()!=0;}
    /** Existing known-default credentials are made unusable, never disclosed or silently activated. */
    @Override public void run(ApplicationArguments arguments){
        tx.executeWithoutResult(status->{
            Integer completed=jdbc.queryForObject("SELECT completed FROM user_password_upgrade_guard WHERE id=1 FOR UPDATE",Integer.class);
            if(Integer.valueOf(1).equals(completed))return;
            long after=0;
            while(true){
                var rows=jdbc.queryForList("SELECT id,password FROM `user` WHERE id>? AND must_change_password=0 ORDER BY id LIMIT 100",after);
                if(rows.isEmpty())break;
                for(var row:rows){
                    after=((Number)row.get("id")).longValue();String hash=row.get("password").toString();
                    if(passwords.matches(com.tianji.user.constants.UserConstants.DEFAULT_PASSWORD,hash))
                        jdbc.update("UPDATE `user` SET password=?,must_change_password=1,temporary_password_expires_at=NULL WHERE id=? AND password=? AND must_change_password=0",passwords.encode(generate()),after,hash);
                }
            }
            jdbc.update("UPDATE user_password_upgrade_guard SET completed=1 WHERE id=1");
        });
    }
}
