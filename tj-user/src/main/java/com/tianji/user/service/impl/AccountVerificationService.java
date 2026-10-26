package com.tianji.user.service.impl;

import com.tianji.common.exceptions.*;
import com.tianji.message.api.client.AsyncSmsClient;
import com.tianji.message.domain.dto.SmsInfoDTO;
import com.tianji.message.domain.enums.SmsTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Purpose-bound, five-minute, single-use proofs. A durable SMS acceptance is not delivery. */
@Service
public class AccountVerificationService {
    private final StringRedisTemplate redis;
    private final AsyncSmsClient sms;
    private final SecureRandom random=new SecureRandom();
    private static final DefaultRedisScript<Long> ISSUE=new DefaultRedisScript<>("""
        if redis.call('EXISTS',KEYS[2])==1 then return 0 end
        local n=redis.call('INCR',KEYS[3]); if n==1 then redis.call('EXPIRE',KEYS[3],3600) end
        if n>10 then return -1 end
        redis.call('SET',KEYS[2],'1','EX',60)
        redis.call('HSET',KEYS[1],'salt',ARGV[1],'digest',ARGV[2],'attempts','0')
        redis.call('EXPIRE',KEYS[1],300)
        return 1
        """,Long.class);
    private static final DefaultRedisScript<Long> CONSUME=new DefaultRedisScript<>("""
        if redis.call('EXISTS',KEYS[1])==0 then return 0 end
        local n=redis.call('HINCRBY',KEYS[1],'attempts',1)
        if n>5 then redis.call('DEL',KEYS[1]); return 0 end
        if redis.call('HGET',KEYS[1],'salt')~=ARGV[1] or redis.call('HGET',KEYS[1],'digest')~=ARGV[2] then
          if n==5 then redis.call('DEL',KEYS[1]) end
          return 0
        end
        redis.call('DEL',KEYS[1]); return 1
        """,Long.class);
    private static final DefaultRedisScript<Long> IP_LIMIT=new DefaultRedisScript<>("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],3600) end; return n",Long.class);
    public AccountVerificationService(StringRedisTemplate redis,AsyncSmsClient sms){this.redis=redis;this.sms=sms;}
    public void issue(String phone,String purpose,String ip){
        validate(phone,purpose);
        Long requests=redis.execute(IP_LIMIT,List.of("account:code:ip:"+digest(ip)),new Object[0]);
        if(requests==null || requests>20)throw new TooManyRequestsException("验证码申请过于频繁，请稍后重试");
        String salt=UUID.randomUUID().toString(),code=String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000)),tag="{account:"+digest(phone)+"}",key=key(phone,purpose);
        Long accepted=redis.execute(ISSUE,List.of(key,tag+":cooldown",tag+":hour"),salt,digest(salt+":"+code));
        if(accepted==null || accepted!=1)throw new TooManyRequestsException("验证码申请过于频繁，请稍后重试");
        var info=new SmsInfoDTO();info.setPhones(List.of(phone));info.setTemplateCode(SmsTemplate.VERIFY_CODE.toString());info.setTemplateParams(Map.of("code",code));
        // If durable acceptance fails, keep the short cooldown, but don't leave a usable undelivered proof.
        try{sms.sendMessage(info);}catch(RuntimeException error){redis.delete(key);throw new ServiceUnavailableException("验证码申请暂未受理，请稍后重试");}
    }
    public void consume(String phone,String purpose,String code){
        validate(phone,purpose);
        if(code==null || !code.matches("[0-9]{6}"))throw new BadRequestException("验证码无效或已过期");
        String key=key(phone,purpose);Object salt=redis.opsForHash().get(key,"salt");String value=Objects.toString(salt,"");
        Long accepted=redis.execute(CONSUME,List.of(key),value,digest(value+":"+code));
        if(accepted==null || accepted!=1)throw new BadRequestException("验证码无效或已过期");
    }
    static String key(String phone,String purpose){return "{account:"+digest(phone)+"}:"+purpose;}
    public static void validate(String phone,String purpose){
        if(phone==null || !phone.matches("1[3-9][0-9]{9}") || purpose==null || !Set.of("REGISTER","RECOVER").contains(purpose))throw new BadRequestException("手机号或验证用途无效");
    }
    static String digest(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception error){throw new IllegalStateException(error);}}
}
