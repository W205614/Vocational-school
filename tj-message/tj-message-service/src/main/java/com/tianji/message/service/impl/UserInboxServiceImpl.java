package com.tianji.message.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.message.config.MessageProperties;
import com.tianji.message.domain.dto.UserInboxDTO;
import com.tianji.message.domain.dto.UserInboxFormDTO;
import com.tianji.message.domain.po.NoticeTemplate;
import com.tianji.message.domain.po.PublicNotice;
import com.tianji.message.domain.po.UserInbox;
import com.tianji.message.domain.query.UserInboxQuery;
import com.tianji.message.enums.NoticeType;
import com.tianji.message.mapper.UserInboxMapper;
import com.tianji.message.service.IPublicNoticeService;
import com.tianji.message.service.IUserInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 用户通知记录 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-19
 */
@Service
@RequiredArgsConstructor
public class UserInboxServiceImpl extends ServiceImpl<UserInboxMapper, UserInbox> implements IUserInboxService {

    private final MessageProperties properties;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final IPublicNoticeService publicNoticeService;

    @Override
    public void saveNoticeToInbox(NoticeTemplate notice, List<UserDTO> users) {
        LocalDateTime pushTime = LocalDateTime.now();
        LocalDateTime expireTime = pushTime.plusMonths(properties.getMessageTtlMonths());
        // 1.初始化信箱数据
        List<UserInbox> list = new ArrayList<>(users.size());
        // 2.组装
        for (UserDTO user : users) {
            UserInbox box = new UserInbox();
            box.setTitle(notice.getTitle());
            box.setContent(notice.getContent());
            box.setUserId(user.getId());
            box.setType(notice.getType());
            box.setPushTime(pushTime);
            box.setExpireTime(expireTime);
            list.add(box);
        }
        // 3.保存
        saveBatch(list);
    }

    @Override
    @Transactional
    public PageDTO<UserInboxDTO> queryUserInBoxesPage(UserInboxQuery query) {
        Long userId=UserContext.requireUser();LocalDateTime now=LocalDateTime.now();
        List<PublicNotice> notices=publicNoticeService.lambdaQuery()
            .gt(PublicNotice::getExpireTime,now).le(PublicNotice::getPushTime,now)
            .apply("NOT EXISTS(SELECT 1 FROM user_inbox i WHERE i.user_id={0} AND i.public_notice_id=public_notice.id)",userId)
            .orderByAsc(PublicNotice::getPushTime).last("LIMIT 100").list();
        if(!notices.isEmpty())saveNoticeListToInbox(notices,userId);
        // 5.分页查询收件箱信息并返回
        Page<UserInbox> userInboxPage = query.toMpPage("push_time", false);
        userInboxPage = lambdaQuery()
                .eq(UserInbox::getUserId, userId)
                .gt(UserInbox::getExpireTime,now)
                .eq(query.getIsRead() != null, UserInbox::getIsRead, query.getIsRead())
                .eq(query.getType() != null, UserInbox::getType, query.getType())
                .page(userInboxPage);
        return PageDTO.of(userInboxPage, UserInboxDTO.class);
    }

    private void saveNoticeListToInbox(List<PublicNotice> notices, Long userId) {
        for(PublicNotice notice:notices)jdbc.update("INSERT IGNORE INTO user_inbox(id,user_id,type,title,content,push_time,expire_time,public_notice_id) VALUES(?,?,?,?,?,?,?,?)",com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),userId,notice.getType(),notice.getTitle(),notice.getContent(),notice.getPushTime(),notice.getExpireTime(),notice.getId());

    }

    @Override
    public Long sentMessageToUser(UserInboxFormDTO userInboxFormDTO) {
        // 1.计算时间
        LocalDateTime pushTime = LocalDateTime.now();
        LocalDateTime expireTime = pushTime.plusMonths(properties.getMessageTtlMonths());
        // 2.获取当前用户
        Long userId = UserContext.getUser();
        // 3.组织数据
        UserInbox inbox = new UserInbox();
        inbox.setUserId(userInboxFormDTO.getUserId());
        inbox.setContent(userInboxFormDTO.getContent());
        inbox.setType(NoticeType.PRIVATE_MESSAGE.getValue());
        inbox.setPushTime(pushTime);
        inbox.setExpireTime(expireTime);
        inbox.setPublisher(userId);
        save(inbox);
        return inbox.getId();
    }
}
