package com.zhixuetang.message.service.impl;

import com.zhixuetang.api.dto.sms.SmsInfoDTO;
import com.zhixuetang.common.exceptions.CommonException;
import com.zhixuetang.message.domain.po.MessageTemplate;
import com.zhixuetang.message.service.*;
import com.zhixuetang.message.thirdparty.ISmsHandler;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class SmsProviderRegistry {
    private final Map<String, ISmsHandler> handlers;
    private final ISmsThirdPlatformService platforms;
    private final INoticeTemplateService notices;
    private final IMessageTemplateService templates;

    public SmsProviderRegistry(
            Map<String, ISmsHandler> handlers,
            ISmsThirdPlatformService platforms,
            INoticeTemplateService notices,
            IMessageTemplateService templates) {
        this.handlers = Map.copyOf(handlers);
        this.platforms = platforms;
        this.notices = notices;
        this.templates = templates;
    }

    MessageTemplate select(SmsInfoDTO info) {
        var notice = notices.queryByCode(info.getTemplateCode());
        if (notice == null
                || !Integer.valueOf(1).equals(notice.getStatus())
                || !Boolean.TRUE.equals(notice.getIsSmsTemplate()))
            throw new CommonException("SMS_NOTICE_TEMPLATE_UNAVAILABLE");
        var available = templates.queryByNoticeTemplateId(notice.getId());
        for (var platform : platforms.queryAllPlatform()) {
            if (!Integer.valueOf(1).equals(platform.getStatus())
                    || !handlers.containsKey(platform.getCode())) continue;
            var matching =
                    available.stream()
                            .filter(
                                    t ->
                                            Integer.valueOf(1).equals(t.getStatus())
                                                    && platform.getCode()
                                                            .equals(t.getPlatformCode()))
                            .toList();
            if (matching.size() > 1) throw new CommonException("SMS_TEMPLATE_MAPPING_CONFLICT");
            if (matching.size() == 1) {
                var value = matching.getFirst();
                if (value.getSignName() == null
                        || value.getSignName().isBlank()
                        || value.getThirdTemplateCode() == null
                        || value.getThirdTemplateCode().isBlank())
                    throw new CommonException("SMS_PROVIDER_TEMPLATE_UNCONFIGURED");
                return value;
            }
        }
        throw new CommonException("SMS_PROVIDER_TEMPLATE_UNAVAILABLE");
    }

    ISmsHandler handler(String code) {
        var handler = handlers.get(code);
        if (handler == null) throw new CommonException("SMS_PROVIDER_UNAVAILABLE");
        return handler;
    }
}
