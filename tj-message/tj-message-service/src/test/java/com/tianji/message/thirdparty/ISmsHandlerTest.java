package com.tianji.message.thirdparty;

import com.tianji.api.dto.sms.SmsInfoDTO;
import com.tianji.message.domain.enums.SmsTemplate;
import com.tianji.message.service.ISmsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;

@SpringBootTest
@EnabledIfEnvironmentVariable(named="TJ_REAL_SMS_TESTS", matches="true")
class ISmsHandlerTest {

    @Autowired
    private ISmsService smsService;

    @Test
    void send() {
        SmsInfoDTO dto = new SmsInfoDTO();
        String phones=System.getenv("TJ_TEST_SMS_PHONES");
        if(phones==null || !phones.matches("1\\d{10}(,1\\d{10})*"))throw new IllegalStateException("Explicit test SMS recipients required");
        dto.setPhones(List.of(phones.split(",")));
        dto.setTemplateCode(SmsTemplate.VERIFY_CODE.name());
        HashMap<String, String> params = new HashMap<>(1);
        params.put("code", "518518");
        dto.setTemplateParams(params);
        smsService.sendMessage(dto);
    }
}
