package com.tianji.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.common.exceptions.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class PublicAccountService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final PasswordEncoder passwords;
    private final AccountVerificationService verification;
    private final boolean enabled;
    private final String policyVersion,policyUrl,deliveryMode;
    public PublicAccountService(JdbcTemplate jdbc,TransactionTemplate tx,PasswordEncoder passwords,AccountVerificationService verification,Environment env){
        this.jdbc=jdbc;this.tx=tx;this.passwords=passwords;this.verification=verification;
        enabled=env.getProperty("tj.accounts.public.enabled",Boolean.class,false);
        policyVersion=env.getProperty("tj.accounts.privacy.policy-version","2026-10-26");
        policyUrl=env.getProperty("tj.accounts.privacy.policy-url","/privacy");
        deliveryMode=env.getProperty("tj.accounts.public.sms-delivery-mode","unconfigured");
        if(policyVersion.isBlank() || policyVersion.length()>64)throw new IllegalArgumentException("A bounded privacy policy version is required");
    }
    public record Registration(String phone,String code,String password,String policyVersion,boolean consent){
        @Override public String toString(){return "Registration[credentials=REDACTED]";}
    }
    public record Recovery(String phone,String code,String newPassword){
        @Override public String toString(){return "Recovery[credentials=REDACTED]";}
    }
    public Map<String,Object> policy(){return Map.of("version",policyVersion,"url",policyUrl,"registrationEnabled",enabled,"verificationDelivery",deliveryMode,"notice","注册需验证手机号并同意隐私规则。注销将禁用账户，履约、交易与审计记录按已发布保留规则处理；不等于立即删除全部历史。");}
    public void requestCode(String phone,String purpose,String ip){
        requireEnabled();
        if(!Set.of("provider","simulated").contains(deliveryMode))throw new ServiceUnavailableException("验证码通知渠道尚未配置");
        verification.issue(phone,purpose,ip);
    }
    public void register(Registration form){
        requireEnabled();validatePassword(form.password());
        if(!form.consent() || !policyVersion.equals(form.policyVersion()))throw new BadRequestException("请阅读并同意当前隐私规则");
        verification.consume(form.phone(),"REGISTER",form.code());
        try{tx.executeWithoutResult(status->{
            long id=IdWorker.getId();jdbc.update("INSERT INTO `user`(id,username,cell_phone,password,type,status,must_change_password) VALUES(?,?,?,?,2,1,0)",id,form.phone(),form.phone(),passwords.encode(form.password()));
            jdbc.update("INSERT INTO user_detail(id,type,name,role_id) VALUES(?,2,?,2)",id,"学员"+form.phone().substring(7));
            jdbc.update("INSERT INTO user_privacy_consent(user_id,policy_version) VALUES(?,?)",id,policyVersion);
        });}catch(org.springframework.dao.DuplicateKeyException error){throw new BadRequestException("无法完成注册，请检查资料或使用密码找回");}
    }
    public void recover(Recovery form){
        requireEnabled();validatePassword(form.newPassword());
        verification.consume(form.phone(),"RECOVER",form.code());
        // Do not disclose existence; frozen/deactivated accounts cannot be reactivated by recovery.
        // Password changes increment auth_version through the existing database trigger.
        jdbc.update("UPDATE `user` SET password=?,must_change_password=0,temporary_password_expires_at=NULL WHERE cell_phone=? AND type=2 AND status=1",passwords.encode(form.newPassword()),form.phone());
    }
    public static void validatePassword(String password){
        if(password==null || password.length()<8 || password.length()>72 || password.getBytes(StandardCharsets.UTF_8).length>72)throw new BadRequestException("密码须为8至72字符且UTF-8不超过72字节");
    }
    private void requireEnabled(){if(!enabled)throw new ServiceUnavailableException("公开注册与找回尚未开放，请联系管理员");}
}
