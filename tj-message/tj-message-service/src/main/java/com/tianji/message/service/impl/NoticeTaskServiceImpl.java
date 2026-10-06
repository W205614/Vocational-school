package com.tianji.message.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.MarkedRunnable;
import com.tianji.common.utils.StringUtils;
import com.tianji.message.constants.MessageErrorInfo;
import com.tianji.message.domain.dto.NoticeTaskDTO;
import com.tianji.message.domain.dto.NoticeTaskFormDTO;
import com.tianji.message.domain.po.NoticeTask;
import com.tianji.message.domain.po.NoticeTemplate;
import com.tianji.message.domain.query.NoticeTaskPageQuery;
import com.tianji.message.enums.TemplateStatus;
import com.tianji.message.mapper.NoticeTaskMapper;
import com.tianji.message.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * <p>
 * 系统通告的任务表，可以延期或定期发送通告 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeTaskServiceImpl extends ServiceImpl<NoticeTaskMapper, NoticeTask> implements INoticeTaskService {

    private final Executor asyncNoticeExecutor;
    private final INoticeTemplateService noticeTemplateService;
    private final UserClient userClient;
    private final IPublicNoticeService publicNoticeService;
    private final IUserInboxService inboxService;
    private final ISmsService smsService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final org.springframework.transaction.PlatformTransactionManager transactions;

    @Override
    @Transactional
    public Long saveNoticeTask(NoticeTaskFormDTO noticeTaskFormDTO) {
        com.tianji.common.utils.UserContext.requireAdmin();validate(noticeTaskFormDTO);
        NoticeTask noticeTask=BeanUtils.copyBean(noticeTaskFormDTO,NoticeTask.class);
        if(noticeTask.getPushTime()==null)noticeTask.setPushTime(LocalDateTime.now());
        save(noticeTask);Long taskId=noticeTask.getId();
        if(Boolean.TRUE.equals(noticeTask.getPartial()))for(Long user:noticeTaskFormDTO.getUserIds().stream().distinct().toList())
            jdbc.update("INSERT INTO notice_task_target(task_id,target_id) VALUES(?,?)",taskId,user);
        return taskId;
    }

    @Override
    public void handleTask(NoticeTask hint) {
        List<UserDTO> users;
        if(Boolean.TRUE.equals(hint.getPartial())){
            List<Long> ids=getBaseMapper().queryTaskTargetByTaskId(hint.getId());
            if(ids==null || ids.isEmpty())throw new com.tianji.common.exceptions.BadRequestException("指定接收人为空，任务不能转为广播");
            users=userClient.queryUserByIds(ids);
            if(users==null || users.size()!=ids.stream().distinct().count())throw new com.tianji.common.exceptions.CommonException("接收人核对未完成");
        }else users=java.util.List.of();
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx->{
            jdbc.queryForMap("SELECT id FROM notice_task WHERE id=? FOR UPDATE",hint.getId());
            NoticeTask task=getById(hint.getId());
            if(task==null || Boolean.TRUE.equals(task.getFinished()) || task.getPushTime().isAfter(LocalDateTime.now()))return;
            if(!java.util.Objects.equals(task.getTemplateId(),hint.getTemplateId()) || !java.util.Objects.equals(task.getPartial(),hint.getPartial()) || !java.util.Objects.equals(task.getUpdateTime(),hint.getUpdateTime()))return;
            if(task.getExpireTime()!=null && !task.getExpireTime().isAfter(LocalDateTime.now())){
                jdbc.update("UPDATE notice_task SET finished=1 WHERE id=?",task.getId());return;
            }
            NoticeTemplate template=noticeTemplateService.getById(task.getTemplateId());
            if(template==null || template.getStatus()!=TemplateStatus.IN_SERVICE.getValue())throw new com.tianji.common.exceptions.BadRequestException("通知模板不可用");
            if(Boolean.TRUE.equals(task.getPartial())){
                inboxService.saveNoticeToInbox(template,users);
                if(Boolean.TRUE.equals(template.getIsSmsTemplate()))smsService.sendMessageByTemplate(template,users);
            }else publicNoticeService.saveNoticeOfTemplate(template);
            boolean repeat=task.getMaxTimes()!=null && task.getMaxTimes()>0;
            jdbc.update("UPDATE notice_task SET finished=?,push_time=?,max_times=GREATEST(COALESCE(max_times,0)-1,0) WHERE id=?",!repeat,repeat?task.getPushTime().plusMinutes(task.getInterval()):task.getPushTime(),task.getId());
        });
    }

    private void validate(NoticeTaskFormDTO form){
        if(form.getTemplateId()==null || form.getName()==null || form.getName().isBlank())throw new com.tianji.common.exceptions.BadRequestException("通知模板与名称不能为空");
        if(form.getPartial()==null)form.setPartial(false);
        if(form.getMaxTimes()==null)form.setMaxTimes(0);
        if(form.getMaxTimes()<0 || form.getMaxTimes()>1000 || form.getMaxTimes()>0 && (form.getInterval()==null || form.getInterval()<=0 || form.getInterval()>Integer.MAX_VALUE))throw new com.tianji.common.exceptions.BadRequestException("重复次数或间隔无效");
        if(Boolean.TRUE.equals(form.getPartial()) && (form.getUserIds()==null || form.getUserIds().isEmpty() || form.getUserIds().size()>100 || form.getUserIds().stream().anyMatch(java.util.Objects::isNull)))throw new com.tianji.common.exceptions.BadRequestException("请指定 1 到 100 位接收人");
    }
    @Override
    @Transactional
    public void updateNoticeTask(NoticeTaskFormDTO noticeTaskFormDTO) {
        com.tianji.common.utils.UserContext.requireAdmin();validate(noticeTaskFormDTO);
        jdbc.queryForMap("SELECT id FROM notice_task WHERE id=? FOR UPDATE",noticeTaskFormDTO.getId());
        jdbc.update("DELETE FROM notice_task_target WHERE task_id=?",noticeTaskFormDTO.getId());
        if(Boolean.TRUE.equals(noticeTaskFormDTO.getPartial()))for(Long user:noticeTaskFormDTO.getUserIds().stream().distinct().toList())jdbc.update("INSERT INTO notice_task_target(task_id,target_id) VALUES(?,?)",noticeTaskFormDTO.getId(),user);
        NoticeTask noticeTask = BeanUtils.copyBean(noticeTaskFormDTO, NoticeTask.class);
        if(noticeTask.getPushTime()==null)noticeTask.setPushTime(LocalDateTime.now());
        updateById(noticeTask);
        jdbc.update("UPDATE notice_task SET delivery_status='PENDING',delivery_attempts=0,delivery_token=NULL,delivery_next_attempt=NOW(3),delivery_error=NULL,delivery_version=delivery_version+1 WHERE id=?",noticeTask.getId());
    }

    @Override
    public PageDTO<NoticeTaskDTO> queryNoticeTasks(NoticeTaskPageQuery query) {
        // 1.分页条件
        Page<NoticeTask> page = query.toMpPage();
        // 2.过滤条件
        page = lambdaQuery()
                .eq(query.getFinished() != null, NoticeTask::getFinished, query.getFinished())
                .like(StringUtils.isNotBlank(query.getKeyword()), NoticeTask::getName, query.getKeyword())
                .ge(query.getMinPushTime() != null, NoticeTask::getPushTime, query.getMinPushTime())
                .le(query.getMaxPushTime() != null, NoticeTask::getPushTime, query.getMaxPushTime())
                .page(page);
        // 3.数据转换
        return PageDTO.of(page, NoticeTaskDTO.class);
    }

    @Override
    public NoticeTaskDTO queryNoticeTask(Long id) {
        NoticeTaskDTO result=BeanUtils.copyBean(getById(id), NoticeTaskDTO.class);
        if(result!=null)result.setUserIds(getBaseMapper().queryTaskTargetByTaskId(id));return result;
    }

    @Override
    public PageDTO<NoticeTask> queryTodoNoticeTaskByPage(int pageNo, int size) {
        // 1.分页查询待发布任务：未完成，发布时间早于当前时间
        Page<NoticeTask> page = lambdaQuery()
                .eq(NoticeTask::getFinished, false)
                .le(NoticeTask::getPushTime, LocalDateTime.now())
                .page(new Page<>(pageNo, size));
        return PageDTO.of(page);
    }
}
