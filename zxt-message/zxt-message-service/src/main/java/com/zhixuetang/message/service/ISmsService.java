package com.zhixuetang.message.service;

import com.zhixuetang.api.dto.sms.SmsInfoDTO;
import com.zhixuetang.api.dto.user.UserDTO;
import com.zhixuetang.message.domain.po.NoticeTemplate;

import java.util.List;

public interface ISmsService {
    void sendMessageByTemplate(NoticeTemplate noticeTemplate, List<UserDTO> users);

    void sendMessage(SmsInfoDTO smsInfoDTO);

    void sendMessageAsync(SmsInfoDTO smsInfoDTO);
}
