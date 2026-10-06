package com.tianji.trade.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.trade.domain.dto.ApproveFormDTO;
import com.tianji.trade.domain.dto.RefundCancelDTO;
import com.tianji.trade.domain.dto.RefundFormDTO;
import com.tianji.trade.domain.query.RefundApplyPageQuery;
import com.tianji.trade.domain.vo.RefundApplyPageVO;
import com.tianji.trade.domain.vo.RefundApplyVO;
import com.tianji.trade.service.IRefundApplyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * <p>
 * 退款申请 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-29
 */
@Tag(name = "退款相关接口")
@RequiredArgsConstructor
@RestController
@RequestMapping("/refund-apply")
public class RefundApplyController {

    private final IRefundApplyService refundApplyService;
    private final com.tianji.common.autoconfigure.reliability.OperationStore operations;

    @Operation(summary = "退款申请")
    @PostMapping
    public org.springframework.http.ResponseEntity<com.tianji.common.autoconfigure.reliability.OperationStore.View> applyRefund(@Valid @RequestBody RefundFormDTO request,@RequestHeader("Idempotency-Key") String key) {

        var form=new com.tianji.trade.service.impl.RefundOperationHandler.Request("APPLY",com.tianji.common.utils.UserContext.getRole(),request,null,null);
        return org.springframework.http.ResponseEntity.accepted().body(operations.submit(com.tianji.common.utils.UserContext.requireUser(),"REFUND_COMMAND",key,form));
    }

    @Operation(summary = "审批退款申请")
    @PutMapping("/approval")
    public org.springframework.http.ResponseEntity<com.tianji.common.autoconfigure.reliability.OperationStore.View> approveRefundApply(@Valid @RequestBody ApproveFormDTO request,@RequestHeader("Idempotency-Key") String key) {
        com.tianji.common.utils.UserContext.requireAdmin();
        var form=new com.tianji.trade.service.impl.RefundOperationHandler.Request("APPROVE",com.tianji.common.utils.UserContext.getRole(),null,request,null);
        return org.springframework.http.ResponseEntity.accepted().body(operations.submit(com.tianji.common.utils.UserContext.requireUser(),"REFUND_COMMAND",key,form));
    }

    @Operation(summary = "取消退款申请")
    @PutMapping("/cancel")
    public org.springframework.http.ResponseEntity<com.tianji.common.autoconfigure.reliability.OperationStore.View> cancelRefundApply(@Valid @RequestBody RefundCancelDTO request,@RequestHeader("Idempotency-Key") String key) {

        var form=new com.tianji.trade.service.impl.RefundOperationHandler.Request("CANCEL",com.tianji.common.utils.UserContext.getRole(),null,null,request);
        return org.springframework.http.ResponseEntity.accepted().body(operations.submit(com.tianji.common.utils.UserContext.requireUser(),"REFUND_COMMAND",key,form));
    }

    @Operation(summary = "分页查询退款申请")
    @GetMapping("/page")
    public PageDTO<RefundApplyPageVO> queryRefundApplyByPage(RefundApplyPageQuery pageQuery){
        return refundApplyService.queryRefundApplyByPage(pageQuery);
    }

    @Operation(summary = "根据id查询退款详情")
    @GetMapping("/{id}")
    public RefundApplyVO queryRefundDetailById(@Parameter(description = "退款id") @PathVariable("id") Long id){
        return refundApplyService.queryRefundDetailById(id);
    }

    @Operation(summary = "根据子订单id查询退款详情")
    @GetMapping("/detail/{id}")
    public RefundApplyVO queryRefundDetailByDetailId(@Parameter(description = "子订单id") @PathVariable("id") Long detailId){
        return refundApplyService.queryRefundDetailByDetailId(detailId);
    }

    @Operation(summary = "查询下一个待审批的退款申请")
    @GetMapping("/next")
    public RefundApplyVO nextRefundApplyToApprove(){
        return refundApplyService.nextRefundApplyToApprove();
    }
}
