package com.tianji.auth.controller;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.LoginFormDTO;
import com.tianji.auth.service.impl.*;
import com.tianji.common.exceptions.*;
import com.tianji.common.utils.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Password-authenticated setup never issues a business token or refresh cookie. */
@RestController @RequiredArgsConstructor @RequestMapping("/accounts/mfa")
public class AdministratorMfaController {
    private final AdministratorMfaService mfa;
    private final UserClient users;
    private final LoginProtection protection;
    @PostMapping("/enroll") public org.springframework.http.ResponseEntity<AdministratorMfaService.Enrollment> enroll(@jakarta.validation.Valid @RequestBody LoginFormDTO form){return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).header("Pragma","no-cache").body(mfa.enroll(administrator(form)));}
    @PostMapping("/confirm") public void confirm(@jakarta.validation.Valid @RequestBody LoginFormDTO form){
        long user=administrator(form);
        try{mfa.confirm(user,form.getMfaCode());protection.success(account(form));}
        catch(BadRequestException | UnauthorizedException | ForbiddenException error){protection.failed(account(form),Objects.toString(WebUtils.getHeader("X-Client-IP"),"unknown"));throw error;}
    }
    private static String account(LoginFormDTO form){return Objects.toString(form.getUsername(),"").trim().toLowerCase(Locale.ROOT);}
    private long administrator(LoginFormDTO form){
        InternalAuth.requireService();
        if(!Integer.valueOf(1).equals(form.getType()))throw new BadRequestException("验证器设置需要账号密码验证");
        String account=Objects.toString(form.getUsername(),"").trim().toLowerCase(Locale.ROOT),ip=Objects.toString(WebUtils.getHeader("X-Client-IP"),"unknown");
        protection.before(account,ip);
        try {
            var user=users.queryUserDetail(form,true);
            if(user==null || Boolean.TRUE.equals(user.getMustChangePassword()))throw new BadRequestException("请先完成初始密码设置");
            var current=users.sessionIdentity(user.getUserId());
            if(current==null || !Long.valueOf(1).equals(current.getRoleId()))throw new ForbiddenException("仅管理员可设置验证器");
            return current.getUserId();
        }catch(BadRequestException | UnauthorizedException | ForbiddenException error){
            protection.failed(account,ip);throw new BadRequestException("无法完成验证器设置，请检查账号及凭据");
        }catch(feign.FeignException error){
            if(error.status()==400 || error.status()==401 || error.status()==403){
                protection.failed(account,ip);throw new BadRequestException("无法完成验证器设置，请检查账号及凭据");
            }
            throw new ServiceUnavailableException("身份服务暂不可用");
        }
    }
}
