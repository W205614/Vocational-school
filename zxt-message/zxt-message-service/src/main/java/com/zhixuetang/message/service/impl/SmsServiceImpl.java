package com.zhixuetang.message.service.impl;

import com.zhixuetang.api.dto.sms.SmsInfoDTO;
import com.zhixuetang.api.dto.user.UserDTO;
import com.zhixuetang.message.domain.po.NoticeTemplate;
import com.zhixuetang.message.service.ISmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SmsServiceImpl implements ISmsService {
    private final SmsDeliveryTasks deliveries;

    @Override
    public void sendMessageByTemplate(NoticeTemplate template, List<UserDTO> users) {
        var info = new SmsInfoDTO();
        info.setPhones(users.stream().map(UserDTO::getCellPhone).distinct().toList());
        info.setTemplateCode(template.getCode());
        sendMessageAsync(info);
    }

    @Override
    public void sendMessage(SmsInfoDTO info) {
        // The public service contract accepts a durable task; the worker owns provider IO.
        deliveries.stage(java.util.UUID.randomUUID().toString(), info);
    }

    @Override
    public void sendMessageAsync(SmsInfoDTO info) {
        sendMessage(info);
    }
}
