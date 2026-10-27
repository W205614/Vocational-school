package com.tianji.user.service.impl;

import com.tianji.message.domain.enums.SmsTemplate;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.RandomUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.message.api.client.AsyncSmsClient;
import com.tianji.message.domain.dto.SmsInfoDTO;
import com.tianji.user.service.ICodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static com.tianji.api.constants.SmsConstants.VERIFY_CODE_PARAM_NAME;
import static com.tianji.common.constants.ErrorInfo.Msg.INVALID_VERIFY_CODE;
import static com.tianji.user.constants.UserConstants.USER_VERIFY_CODE_KEY;
import static com.tianji.user.constants.UserConstants.USER_VERIFY_CODE_TTL;

@Slf4j
@Service
public class CodeServiceImpl implements ICodeService {

    // One Redis key keeps comparison, attempt counting and consumption atomic,
    // including on clustered Redis. The code's original expiry is never extended.
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> CONSUME =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>("""
                local value=redis.call('GET',KEYS[1]); if not value then return 0 end
                local expected,attempts=string.match(value,'^(%d%d%d%d):(%d+)$')
                if not expected then
                  if not string.match(value,'^%d%d%d%d$') then redis.call('DEL',KEYS[1]); return 0 end
                  expected=value; attempts=0
                end
                if expected==ARGV[1] then redis.call('DEL',KEYS[1]); return 1 end
                attempts=tonumber(attempts)+1
                if attempts>=5 then redis.call('DEL',KEYS[1])
                else redis.call('SET',KEYS[1],expected..':'..attempts,'KEEPTTL') end
                return 0
                """,Long.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private AsyncSmsClient asyncSmsClient;

    @Override
    public void sendVerifyCode(String phone) {
        String key = USER_VERIFY_CODE_KEY + phone;
        // Redis selects one code atomically; every concurrently accepted SMS carries that value.
        String candidate = RandomUtils.randomNumbers(4);
        String code = stringRedisTemplate.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<String>(
                "local v=redis.call('GET',KEYS[1]); if v then return string.match(v,'^(%d%d%d%d):%d+$') or v end; redis.call('SET',KEYS[1],ARGV[1],'EX',ARGV[2]); return ARGV[1]",String.class),
                java.util.List.of(key),candidate,String.valueOf(USER_VERIFY_CODE_TTL.toSeconds()));
        SmsInfoDTO info = new SmsInfoDTO();
        info.setPhones(CollUtils.singletonList(phone));
        info.setTemplateCode(SmsTemplate.VERIFY_CODE.toString());
        Map<String, String> params = new HashMap<>(1);
        params.put(VERIFY_CODE_PARAM_NAME, code);
        info.setTemplateParams(params);
        asyncSmsClient.sendMessage(info);
    }

    @Override
    public void verifyCode(String phone, String code) {
        if (StringUtils.isBlank(phone) || code==null || !code.matches("[0-9]{4}"))
            throw new BadRequestException(INVALID_VERIFY_CODE);
        Long accepted=stringRedisTemplate.execute(CONSUME,java.util.List.of(USER_VERIFY_CODE_KEY+phone),code);
        if (!Long.valueOf(1).equals(accepted)) {
            throw new BadRequestException(INVALID_VERIFY_CODE);
        }
    }
}
