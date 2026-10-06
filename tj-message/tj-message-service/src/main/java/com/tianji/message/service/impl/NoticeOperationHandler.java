package com.tianji.message.service.impl;
import com.tianji.common.autoconfigure.reliability.OperationHandler;import com.tianji.common.utils.UserContext;import com.tianji.message.domain.dto.NoticeTaskFormDTO;import com.tianji.message.service.INoticeTaskService;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import tools.jackson.databind.json.JsonMapper;
@Service @RequiredArgsConstructor public class NoticeOperationHandler implements OperationHandler{
 private final INoticeTaskService notices;private final JsonMapper json;
 public String kind(){return "NOTICE_TASK_CREATE";}
 public Object execute(String operation,long user,String payload){UserContext.setUser(user);UserContext.setRole(1L);try{return java.util.Map.of("taskId",notices.saveNoticeTask(json.readValue(payload,NoticeTaskFormDTO.class)));}finally{UserContext.removeUser();}}
}
