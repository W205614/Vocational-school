package com.tianji.exam.service.impl;
import com.tianji.common.autoconfigure.reliability.OperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
@Service @RequiredArgsConstructor
public class ExamPublicationHandler implements OperationHandler {
 private final ExamWorkflowService service;private final JsonMapper json;
 @Override public String kind(){return "EXAM_PUBLISH";}
 @Override public Object execute(String operation,long user,String payload){return service.publish(json.readValue(payload,ExamWorkflowService.Publish.class));}
}
