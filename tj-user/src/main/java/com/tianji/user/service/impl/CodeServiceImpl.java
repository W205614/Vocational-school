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
                "local v=redis.call('GET',KEYS[1]); if v then return v end; redis.call('SET',KEYS[1],ARGV[1],'EX',ARGV[2]); return ARGV[1]",String.class),
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
        String cacheCode = stringRedisTemplate.opsForValue().get(USER_VERIFY_CODE_KEY + phone);
        if (!StringUtils.equals(cacheCode, code)) {
            // 验证码错误
            throw new BadRequestException(INVALID_VERIFY_CODE);
        }
    }
}
