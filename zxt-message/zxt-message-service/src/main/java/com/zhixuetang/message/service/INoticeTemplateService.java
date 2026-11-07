package com.zhixuetang.message.service;

import com.zhixuetang.message.domain.dto.NoticeTemplateDTO;
import com.zhixuetang.message.domain.dto.NoticeTemplateFormDTO;
import com.zhixuetang.message.domain.query.NoticeTemplatePageQuery;
import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.message.domain.po.NoticeTemplate;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 * 通知模板 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-19
 */
public interface INoticeTemplateService extends IService<NoticeTemplate> {

    Long saveNoticeTemplate(NoticeTemplateFormDTO noticeTemplateFormDTO);

    void updateNoticeTemplate(NoticeTemplateFormDTO noticeTemplateFormDTO);

    PageDTO<NoticeTemplateDTO> queryNoticeTemplates(NoticeTemplatePageQuery pageQuery);

    NoticeTemplateDTO queryNoticeTemplate(Long id);

    NoticeTemplate queryByCode(String code);
}
