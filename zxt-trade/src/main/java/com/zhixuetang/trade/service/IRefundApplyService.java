package com.zhixuetang.trade.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.pay.sdk.dto.RefundResultDTO;
import com.zhixuetang.trade.domain.dto.ApproveFormDTO;
import com.zhixuetang.trade.domain.dto.RefundCancelDTO;
import com.zhixuetang.trade.domain.dto.RefundFormDTO;
import com.zhixuetang.trade.domain.po.RefundApply;
import com.zhixuetang.trade.domain.query.RefundApplyPageQuery;
import com.zhixuetang.trade.domain.vo.RefundApplyPageVO;
import com.zhixuetang.trade.domain.vo.RefundApplyVO;

import java.util.List;

/**
 * <p>
 * 退款申请 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-29
 */
public interface IRefundApplyService extends IService<RefundApply> {

    List<RefundApply> queryByDetailId(Long id);

    void applyRefund(RefundFormDTO refundFormDTO);

    PageDTO<RefundApplyPageVO> queryRefundApplyByPage(RefundApplyPageQuery pageQuery);

    RefundApplyVO queryRefundDetailById(Long id);

    RefundApplyVO nextRefundApplyToApprove();

    void approveRefundApply(ApproveFormDTO approveDTO);

    void cancelRefundApply(RefundCancelDTO cancelDTO);

    RefundApplyVO queryRefundDetailByDetailId(Long id);

    void handleRefundResult(RefundResultDTO refundResult);

    List<RefundApply> queryApplyToSend(int page, int size);

    void sendRefundRequest(RefundApply refundApply);

    boolean checkRefundStatus(RefundApply refundApply);
}
