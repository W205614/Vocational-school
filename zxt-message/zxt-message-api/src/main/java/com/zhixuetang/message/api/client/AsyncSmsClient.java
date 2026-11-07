package com.zhixuetang.message.api.client;

import com.zhixuetang.common.autoconfigure.reliability.OutboxStore;
import com.zhixuetang.common.constants.MqConstants;
import com.zhixuetang.message.domain.dto.SmsInfoDTO;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;

/** Accept SMS into a local transaction; delivery does not depend on broker availability. */
public class AsyncSmsClient {
    private final OutboxStore outbox;
    private final TransactionTemplate transactions;

    public AsyncSmsClient(OutboxStore outbox, PlatformTransactionManager manager) {
        this.outbox = outbox;
        this.transactions = new TransactionTemplate(manager);
    }

    public void sendMessage(SmsInfoDTO info) {
        if (info == null || info.getPhones() == null || info.getTemplateCode() == null)
            throw new IllegalArgumentException("短信参数无效");
        transactions.executeWithoutResult(
                status ->
                        outbox.enqueue(
                                "sms:" + UUID.randomUUID(),
                                MqConstants.Exchange.SMS_EXCHANGE,
                                MqConstants.Key.SMS_MESSAGE,
                                info));
    }
}
