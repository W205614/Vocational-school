package com.zhixuetang.message.service;

import com.zhixuetang.message.domain.dto.NoticeTaskDTO;
import com.zhixuetang.message.domain.dto.NoticeTaskFormDTO;
import com.zhixuetang.message.domain.query.NoticeTaskPageQuery;
import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.message.domain.po.NoticeTask;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 * 系统通告的任务表，可以延期或定期发送通告 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-19
 */
public interface INoticeTaskService extends IService<NoticeTask> {

    Long saveNoticeTask(NoticeTaskFormDTO noticeTaskFormDTO);

    void handleTask(NoticeTask noticeTask);

    void updateNoticeTask(NoticeTaskFormDTO noticeTaskFormDTO);

    PageDTO<NoticeTaskDTO> queryNoticeTasks(NoticeTaskPageQuery pageQuery);

    NoticeTaskDTO queryNoticeTask(Long id);

    PageDTO<NoticeTask> queryTodoNoticeTaskByPage(int pageNo, int size);
}
