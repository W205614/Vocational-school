package com.tianji.trade.service.impl;
import com.tianji.common.autoconfigure.reliability.OperationHandler;
import com.tianji.common.utils.UserContext;
import com.tianji.trade.domain.dto.*;
import com.tianji.trade.service.IRefundApplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;
@Service @RequiredArgsConstructor
public class RefundOperationHandler implements OperationHandler {
 private final IRefundApplyService refunds;private final JsonMapper json;
 public record Request(String action,Long role,RefundFormDTO apply,ApproveFormDTO approval,RefundCancelDTO cancel){}
 @Override public String kind(){return "REFUND_COMMAND";}
 @Override public Object execute(String operation,long user,String payload){
  Request request=json.readValue(payload,Request.class);UserContext.setUser(user);UserContext.setRole(request.role());
  try{switch(request.action()){case "APPLY"->refunds.applyRefund(request.apply());case "APPROVE"->refunds.approveRefundApply(request.approval());case "CANCEL"->refunds.cancelRefundApply(request.cancel());default->throw new com.tianji.common.exceptions.BadRequestException("退款操作无效");}
   return Map.of("accepted",true);
  }finally{UserContext.removeUser();}
 }
}
