package com.zhixuetang.message.service;

import com.zhixuetang.message.domain.dto.UserInboxDTO;
import com.zhixuetang.message.domain.dto.UserInboxFormDTO;
import com.zhixuetang.api.dto.user.UserDTO;
import com.zhixuetang.message.domain.query.UserInboxQuery;
import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.message.domain.po.NoticeTemplate;
import com.zhixuetang.message.domain.po.UserInbox;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * <p>
 * 用户通知记录 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-19
 */
public interface IUserInboxService extends IService<UserInbox> {

    void saveNoticeToInbox(NoticeTemplate noticeTemplate, List<UserDTO> users);

    PageDTO<UserInboxDTO> queryUserInBoxesPage(UserInboxQuery query);

    Long sentMessageToUser(UserInboxFormDTO userInboxFormDTO);
}
