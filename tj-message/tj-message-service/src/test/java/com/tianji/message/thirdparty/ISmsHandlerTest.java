package com.tianji.message.thirdparty;

import com.tianji.api.dto.sms.SmsInfoDTO;
import com.tianji.message.domain.enums.SmsTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "TJ_REAL_SMS_TESTS", matches = "true")
class ISmsHandlerTest {

    @Autowired private com.tianji.message.service.impl.SmsDeliveryTasks deliveries;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void explicitlyConfiguredRecipientsReceiveProviderDeliveryReceipts() throws Exception {
        SmsInfoDTO dto = new SmsInfoDTO();
        String phones = System.getenv("TJ_TEST_SMS_PHONES");
        if (phones == null || !phones.matches("1[0-9]{10}(,1[0-9]{10})*"))
            throw new IllegalStateException("Explicit test SMS recipients required");
        dto.setPhones(List.of(phones.split(",")));
        dto.setTemplateCode(SmsTemplate.VERIFY_CODE.name());
        HashMap<String, String> params = new HashMap<>(1);
        params.put("code", "518518");
        dto.setTemplateParams(params);
        dto.setExpiresAt(System.currentTimeMillis() + 300_000);
        String id = java.util.UUID.randomUUID().toString();
        deliveries.stage(id, dto);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
        while (System.nanoTime() < deadline) {
            deliveries.poll();
            var states =
                    jdbc.queryForList(
                            "SELECT state FROM sms_delivery_recipient WHERE task_id=?",
                            String.class,
                            id);
            if (!states.isEmpty() && states.stream().allMatch("DELIVERED"::equals)) return;
            if (states.stream()
                    .anyMatch(java.util.Set.of("FAILED", "EXPIRED", "SIMULATED")::contains))
                org.junit.jupiter.api.Assertions.fail(
                        "No real delivered receipt for SMS task " + id);
            Thread.sleep(1000);
        }
        org.junit.jupiter.api.Assertions.fail(
                "Provider delivery was not verified before the test deadline; task " + id);
    }
}
