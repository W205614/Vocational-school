package com.zhixuetang.message.service;

import com.zhixuetang.message.domain.po.NoticeTemplate;
import com.zhixuetang.message.domain.po.PublicNotice;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 * 公告消息模板 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-19
 */
public interface IPublicNoticeService extends IService<PublicNotice> {

    void saveNoticeOfTemplate(NoticeTemplate noticeTemplate);
}
