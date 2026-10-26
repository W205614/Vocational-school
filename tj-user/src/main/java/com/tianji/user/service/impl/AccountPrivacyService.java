package com.tianji.user.service.impl;

import com.tianji.common.exceptions.*;
import com.tianji.common.utils.UserContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Account-data export and retention-aware deactivation. Never erases financial/exam history implicitly. */
@Service
public class AccountPrivacyService {
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final PasswordEncoder passwords;private final PublicAccountService accounts;private final int retentionDays;
    public AccountPrivacyService(JdbcTemplate jdbc,TransactionTemplate tx,PasswordEncoder passwords,PublicAccountService accounts,Environment env){
        this.jdbc=jdbc;this.tx=tx;this.passwords=passwords;this.accounts=accounts;retentionDays=env.getProperty("tj.accounts.privacy.retention-review-days",Integer.class,30);
        if(retentionDays<1 || retentionDays>3650)throw new IllegalArgumentException("Privacy retention review must be explicitly bounded");
    }
    public Map<String,Object> export(String password){
        long user=UserContext.requireUser();authenticate(user,password,false);
        Map<String,Object> export=new LinkedHashMap<>();export.put("scope","ACCOUNT_PROFILE_AND_CONSENT");
        export.put("profile",jdbc.queryForMap("SELECT u.id,u.username,u.cell_phone,u.type,u.status,u.create_time,d.name,d.gender,d.email,d.qq,d.birthday,d.job,d.province,d.city,d.district,d.intro FROM `user` u JOIN user_detail d ON d.id=u.id WHERE u.id=?",user));
        export.put("consents",jdbc.queryForList("SELECT policy_version,accepted_at FROM user_privacy_consent WHERE user_id=? ORDER BY accepted_at",user));
        export.put("retainedDomains",List.of("orders/payments/refunds","learning/exams","security/audit"));
        export.put("request",request(user,"EXPORT"));
        export.put("notice","此下载仅包含账户资料与同意记录；完整跨业务导出已登记为待运营审核，交易、学习与审计数据不会被误标为已导出。");return export;
    }
    public Map<String,Object> deactivate(String password,String confirmation){
        long user=UserContext.requireUser();if(!"注销账户".equals(confirmation))throw new BadRequestException("请输入“注销账户”确认");
        return tx.execute(status->{
            authenticate(user,password,true);
            var existing=jdbc.queryForList("SELECT id,status,retain_until FROM user_deletion_request WHERE user_id=?",user);if(!existing.isEmpty())return existing.getFirst();
            String id=UUID.randomUUID().toString(),version=accounts.policy().get("version").toString();
            jdbc.update("INSERT INTO user_deletion_request(id,user_id,policy_version,retain_until) VALUES(?,?,?,DATE_ADD(NOW(3),INTERVAL ? DAY))",id,user,version,retentionDays);
            jdbc.update("UPDATE `user` SET status=0,password=?,must_change_password=0,temporary_password_expires_at=NULL WHERE id=?",passwords.encode(UUID.randomUUID()+UUID.randomUUID().toString()),user);
            jdbc.update("UPDATE user_detail SET name='已注销学员',icon=NULL,email=NULL,qq=NULL,birthday=NULL,job=NULL,province=NULL,city=NULL,district=NULL,intro=NULL,photo=NULL WHERE id=?",user);
            Map<String,Object> receipt=new LinkedHashMap<>(jdbc.queryForMap("SELECT id,status,retain_until FROM user_deletion_request WHERE id=?",id));receipt.put("request",request(user,"DELETE"));receipt.put("notice","账户已禁用，公开资料已清除；跨业务清理仍待运营审核，交易和安全记录按保留规则处理。");return receipt;
        });
    }
    public void consent(String version){
        long user=UserContext.requireUser();if(!Objects.equals(version,accounts.policy().get("version")))throw new BadRequestException("隐私规则版本已变化，请重新阅读");
        jdbc.update("INSERT IGNORE INTO user_privacy_consent(user_id,policy_version) VALUES(?,?)",user,version);
    }
    private Map<String,Object> request(long user,String kind){
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user_privacy_request(id,user_id,kind,retained_domains) VALUES(?,?,?,'orders/payments/refunds,learning/exams,security/audit') ON DUPLICATE KEY UPDATE user_id=user_id",id,user,kind);
        return jdbc.queryForMap("SELECT id,kind,scope,status,retained_domains,requested_at,version FROM user_privacy_request WHERE user_id=? AND kind=? AND status IN('PENDING_REVIEW','IN_PROGRESS')",user,kind);
    }
    public List<Map<String,Object>> pending(){UserContext.requireAdmin();return jdbc.queryForList("SELECT id,user_id,kind,scope,status,retained_domains,requested_at,version FROM user_privacy_request WHERE status IN('PENDING_REVIEW','IN_PROGRESS') ORDER BY requested_at,id LIMIT 100");}
    public record Resolution(long version,String status,String reason,String evidenceReference){}
    public void resolve(String id,Resolution form){
        UserContext.requireAdmin();long admin=UserContext.requireUser();
        if(id==null || !id.matches("[0-9a-f-]{36}") || form.status()==null || !Set.of("IN_PROGRESS","COMPLETED","REJECTED").contains(form.status()) || form.reason()==null || form.reason().isBlank() || form.reason().length()>1000)throw new BadRequestException("请提供处理状态、版本与处理依据");
        if("COMPLETED".equals(form.status()) && (form.evidenceReference()==null || form.evidenceReference().isBlank()))throw new BadRequestException("完成前须记录已交付导出或按保留策略完成清理的证据引用");
        if(form.evidenceReference()!=null && form.evidenceReference().length()>255)throw new BadRequestException("证据引用过长");
        if(jdbc.update("UPDATE user_privacy_request SET status=?,resolution=?,evidence_reference=?,reviewed_by=?,reviewed_at=NOW(3),version=version+1 WHERE id=? AND version=? AND status IN('PENDING_REVIEW','IN_PROGRESS')",form.status(),form.reason(),form.evidenceReference(),admin,id,form.version())!=1)throw new ConflictException("申请已变化，请重新核对");
    }
    private void authenticate(long user,String password,boolean lock){
        var rows=jdbc.queryForList("SELECT password,type,status FROM `user` WHERE id=?"+(lock?" FOR UPDATE":""),user);
        if(password==null || password.length()>72 || rows.isEmpty() || !passwords.matches(password,rows.getFirst().get("password").toString()))throw new UnauthorizedException("当前密码验证失败");
        if(lock && ((Number)rows.getFirst().get("type")).intValue()!=2)throw new ForbiddenException("教师与管理账号请由管理员处理注销交接");
        if(((Number)rows.getFirst().get("status")).intValue()!=1)throw new UnauthorizedException("账户已禁用");
    }
}
