package com.tianji.trade.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tianji.api.cache.RoleCache;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.Constant;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.enums.UserType;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.*;
import com.tianji.pay.sdk.client.PayClient;
import com.tianji.pay.sdk.constants.PayChannel;
import com.tianji.pay.sdk.constants.RefundChannelEnum;
import com.tianji.pay.sdk.dto.RefundApplyDTO;
import com.tianji.pay.sdk.dto.RefundResultDTO;
import com.tianji.trade.constants.OrderStatus;
import com.tianji.trade.constants.RefundStatus;
import com.tianji.trade.constants.TradeErrorInfo;
import com.tianji.trade.domain.dto.ApproveFormDTO;
import com.tianji.trade.domain.dto.RefundCancelDTO;
import com.tianji.trade.domain.dto.RefundFormDTO;
import com.tianji.trade.domain.po.Order;
import com.tianji.trade.domain.po.OrderDetail;
import com.tianji.trade.domain.po.RefundApply;
import com.tianji.trade.domain.query.RefundApplyPageQuery;
import com.tianji.trade.domain.vo.RefundApplyPageVO;
import com.tianji.trade.domain.vo.RefundApplyVO;
import com.tianji.trade.mapper.OrderMapper;
import com.tianji.trade.mapper.RefundApplyMapper;
import com.tianji.trade.service.IOrderDetailService;
import com.tianji.trade.service.IRefundApplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.tianji.trade.constants.RefundStatus.AGREE;
import static com.tianji.trade.constants.RefundStatus.REJECT;

/**
 * <p>
 * 退款申请 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-29
 */
@Service
@RequiredArgsConstructor
public class RefundApplyServiceImpl extends ServiceImpl<RefundApplyMapper, RefundApply> implements IRefundApplyService {

    private final OrderMapper orderMapper;
    private final IOrderDetailService detailService;
    private final UserClient userClient;
    private final PayClient payClient;
    private final RoleCache roleCache;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final org.springframework.beans.factory.ObjectProvider<IRefundApplyService> self;
    private final com.tianji.common.autoconfigure.reliability.OutboxStore outbox;
    private final RabbitMqHelper rabbitMqHelper;

    @Override
    public List<RefundApply> queryByDetailId(Long id) {
        // 1.根据id倒序查询，最新的退款申请在最前面
        List<RefundApply> list = baseMapper.queryByDetailId(id);
        // 2.判空
        if (CollUtils.isEmpty(list)) {
            return CollUtils.emptyList();
        }
        return list;
    }

    @Override @Transactional
    public void applyRefund(RefundFormDTO form) {
        long user=UserContext.requireUser();boolean admin=Long.valueOf(1).equals(UserContext.getRole());
        OrderDetail hint=detailService.getById(form.getOrderDetailId());
        if(hint==null || !admin && !Objects.equals(hint.getUserId(),user)) throw new BadRequestException(TradeErrorInfo.ORDER_NOT_EXISTS);
        Order order=orderMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Order>().eq("id",hint.getOrderId()).last("FOR UPDATE"));
        jdbc.queryForMap("SELECT id FROM order_detail WHERE id=? FOR UPDATE",hint.getId());
        OrderDetail detail=detailService.getById(hint.getId());
        if(order==null || !Set.of(2,6).contains(order.getStatus()) || detail.getRealPayAmount()<=0) throw new BizIllegalException(TradeErrorInfo.ORDER_CANNOT_REFUND);
        List<RefundApply> previous=queryByDetailId(detail.getId());
        if(previous.stream().anyMatch(r->RefundStatus.inProgress(r.getStatus())))return;
        if(previous.stream().anyMatch(r->RefundStatus.SUCCESS.equalsValue(r.getStatus())))throw new BizIllegalException("该订单条目已退款");
        if(!admin && previous.size()>=2)throw new BizIllegalException(TradeErrorInfo.REFUND_TOO_MANY_TIMES);
        RefundApply apply=new RefundApply().setOrderId(order.getId()).setOrderDetailId(detail.getId()).setUserId(detail.getUserId())
          .setRefundAmount(detail.getRealPayAmount()).setRefundReason(form.getRefundReason()).setQuestionDesc(form.getQuestionDesc())
          .setCreater(user).setStatus(admin?3:1).setMessage(admin?"管理员退款":"用户申请退款");
        if(!save(apply))throw new DbException("退款申请保存失败");
        jdbc.update("UPDATE `order` SET status=6,refund_time=NOW(),message='申请退款' WHERE id=? AND status IN(2,6)",order.getId());
        jdbc.update("UPDATE order_detail SET status=6,refund_status=? WHERE id=? AND status IN(2,6)",apply.getStatus(),detail.getId());
        if(admin)sendRefundRequestAsync(apply);
    }

    @Override
    public PageDTO<RefundApplyPageVO> queryRefundApplyByPage(RefundApplyPageQuery q) {
        // 1.分页和排序条件
        Page<RefundApply> p = searchRefundApply(q);

        // 2.数据处理
        List<RefundApply> records = p.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(p);
        }
        // 3.获取用户信息
        Map<Long, UserDTO> userMap = getRefundUserInfo(records);
        // 4.vo转换
        List<RefundApplyPageVO> list = new ArrayList<>(records.size());
        for (RefundApply r : records) {
            RefundApplyPageVO v = BeanUtils.copyBean(r, RefundApplyPageVO.class);
            list.add(v);
            // 4.1.申请人
            UserDTO u = userMap.get(r.getCreater());
            v.setProposerName(roleCache.exchangeRoleName(u));
            v.setProposerMobile(u == null ? null : u.getCellPhone());
            // 4.2.审批人
            v.setApproverName(roleCache.exchangeRoleName(userMap.get(r.getApprover())));
            // 4.3.退款状态
            v.setRefundStatusDesc(RefundStatus.desc(r.getStatus()));
            if (RefundStatus.SUCCESS.equalsValue(r.getStatus())) {
                v.setRefundSuccessTime(r.getFinishTime());
            }
        }
        return PageDTO.of(p, list);
    }

    private Page<RefundApply> searchRefundApply(RefundApplyPageQuery q) {
        Integer refundStatus = q.getRefundStatus();
        String defaultSortBy = "id";
        boolean isAsc = false;
        if (refundStatus != null) {
            if (RefundStatus.UN_APPROVE.equalsValue(refundStatus)) {
                defaultSortBy = Constant.DATA_FIELD_NAME_CREATE_TIME;
            } else {
                defaultSortBy = "approve_time";
                isAsc = false;
            }
        }
        Page<RefundApply> p = q.toMpPage(defaultSortBy, isAsc);

        // 2.学生条件
        Long userId = Long.valueOf(1).equals(UserContext.getRole())?null:UserContext.requireUser();
        if (Long.valueOf(1).equals(UserContext.getRole()) && StringUtils.isNotBlank(q.getMobile())) {
            userId = userClient.exchangeUserIdWithPhone(q.getMobile());
            if (userId == null) {
                // 学生不存在，则返回空数据
                return Page.of(0, 0);
            }
        }

        // 3.分页搜索
        p = lambdaQuery()
                .eq(q.getId() != null, RefundApply::getId, q.getId())
                .eq(refundStatus != null, RefundApply::getStatus, refundStatus)
                .eq(q.getOrderDetailId() != null, RefundApply::getOrderDetailId, q.getOrderDetailId())
                .eq(q.getOrderId() != null, RefundApply::getOrderId, q.getOrderId())
                .eq(userId != null, RefundApply::getUserId, userId)
                .ge(q.getApplyStartTime() != null, RefundApply::getCreateTime, q.getApplyStartTime())
                .le(q.getApplyEndTime() != null, RefundApply::getCreateTime, q.getApplyEndTime())
                .page(p);
        return p;
    }

    private Map<Long, UserDTO> getRefundUserInfo(List<RefundApply> records) {
        Set<Long> uIds = new HashSet<>();
        for (RefundApply record : records) {
            uIds.add(record.getCreater());
            uIds.add(record.getApprover());
        }
        uIds.removeIf(id->id==null || id<=0);
        List<UserDTO> userDTOS = uIds.isEmpty()?List.of():userClient.queryUserByIds(uIds);

        return userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
    }

    @Override
    public RefundApplyVO queryRefundDetailById(Long id) {
        // 1.查询退款数据
        RefundApply apply = getById(id);
        if(apply!=null && !Long.valueOf(1).equals(UserContext.getRole()) && !Objects.equals(apply.getUserId(),UserContext.requireUser())) throw new BadRequestException(TradeErrorInfo.REFUND_NOT_EXISTS);
        if (apply == null) {
            throw new BadRequestException(TradeErrorInfo.REFUND_NOT_EXISTS);
        }
        // 2.转换VO
        RefundApplyVO vo = BeanUtils.copyBean(apply, RefundApplyVO.class);

        // 3.查询订单信息及交易流水
        Order order = orderMapper.getById(apply.getOrderId());
        if (order == null) {
            throw new BadRequestException(TradeErrorInfo.ORDER_NOT_EXISTS);
        }
        vo.setPayOrderNo(order.getPayOrderNo());
        vo.setPayChannel(PayChannel.desc(order.getPayChannel()));
        vo.setRefundChannel(RefundChannelEnum.desc(apply.getRefundChannel()));
        vo.setOrderTime(order.getCreateTime());
        vo.setPaySuccessTime(order.getPayTime());

        // 4.用户信息
        Set<Long> uIds = new HashSet<>(2);
        uIds.add(apply.getUserId());
        uIds.add(apply.getCreater());
        // 4.1.远程查询
        List<UserDTO> userDTOS = uIds.isEmpty()?List.of():userClient.queryUserByIds(uIds);
        AssertUtils.isNotEmpty(userDTOS, TradeErrorInfo.COURSE_EXPIRED);
        Map<Long, UserDTO> userMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        // 4.2.学员
        UserDTO student = userMap.get(apply.getUserId());
        vo.setStudentName(roleCache.exchangeRoleName(student));
        vo.setMobile(student==null?null:student.getCellPhone());
        // 4.3.申请人
        vo.setRefundProposerName(roleCache.exchangeRoleName(userMap.get(apply.getCreater())));

        // 6.订单详情
        OrderDetail detail = detailService.getBaseMapper().selectById(apply.getOrderDetailId());
        if (detail == null) {
            throw new BadRequestException(TradeErrorInfo.ORDER_NOT_EXISTS);
        }
        vo.setName(detail.getName());
        vo.setPrice(detail.getPrice());
        vo.setRealPayAmount(detail.getRealPayAmount());
        vo.setDiscountAmount(detail.getDiscountAmount());

        return vo;
    }

    @Override
    public RefundApplyVO nextRefundApplyToApprove() {
        UserContext.requireAdmin();
        // 1.查询一个待处理的申请单
        Long id = baseMapper.nextRefundApplyToApprove();
        // 2.查询数据并返回
        return queryRefundDetailById(id);
    }

    @Override @Transactional
    public void approveRefundApply(ApproveFormDTO form) {
        UserContext.requireAdmin();
        RefundApply hint=getById(form.getId());
        if(hint==null)throw new BadRequestException(TradeErrorInfo.REFUND_NOT_EXISTS);
        jdbc.queryForMap("SELECT id FROM `order` WHERE id=? FOR UPDATE",hint.getOrderId());
        RefundApply apply=baseMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RefundApply>().eq("id",form.getId()).last("FOR UPDATE"));
        if(apply==null)throw new BadRequestException(TradeErrorInfo.REFUND_NOT_EXISTS);
        int target=form.getApproveType()==1?3:4;
        if(apply.getStatus()==target)return;
        if(apply.getStatus()!=1)throw new com.tianji.common.exceptions.ConflictException("退款申请已处理");
        jdbc.update("UPDATE refund_apply SET status=?,approver=?,approve_time=NOW(),approve_opinion=?,remark=?,message=? WHERE id=? AND status=1",target,UserContext.getUser(),form.getApproveOpinion(),form.getRemark(),RefundStatus.desc(target),apply.getId());
        jdbc.update("UPDATE order_detail SET refund_status=?,status=? WHERE id=?",target,target==4?2:6,apply.getOrderDetailId());
        refreshRefundAggregate(apply.getOrderId());
        if(target==3)sendRefundRequestAsync(apply);
    }
    @Override @Transactional
    public void cancelRefundApply(RefundCancelDTO form) {
        long user=UserContext.requireUser();
        if(form.getId()==null && form.getOrderDetailId()==null)throw new BadRequestException("退款标识不能为空");
        var wrapper=new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RefundApply>();
        if(form.getId()!=null)wrapper.eq("id",form.getId());else wrapper.eq("order_detail_id",form.getOrderDetailId()).orderByDesc("id");
        if(!Long.valueOf(1).equals(UserContext.getRole()))wrapper.eq("user_id",user);
        wrapper.last("LIMIT 1");
        var hint=baseMapper.selectOne(wrapper);
        if(hint==null)throw new BadRequestException(TradeErrorInfo.REFUND_NOT_EXISTS);
        jdbc.queryForMap("SELECT id FROM `order` WHERE id=? FOR UPDATE",hint.getOrderId());
        var apply=baseMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RefundApply>().eq("id",hint.getId()).last("FOR UPDATE"));
        if(apply==null)throw new BadRequestException(TradeErrorInfo.REFUND_NOT_EXISTS);
        if(apply.getStatus()==2)return;
        if(apply.getStatus()!=1)throw new com.tianji.common.exceptions.ConflictException("退款申请已审批");
        jdbc.update("UPDATE refund_apply SET status=2,message='用户取消退款' WHERE id=? AND status=1",apply.getId());
        jdbc.update("UPDATE order_detail SET refund_status=2,status=2 WHERE id=?",apply.getOrderDetailId());
        refreshRefundAggregate(apply.getOrderId());
    }

    @Override
    public RefundApplyVO queryRefundDetailByDetailId(Long detailId) {
        // 1.查询申请记录
        List<RefundApply> refundApplies = queryByDetailId(detailId);
        if (CollUtils.isEmpty(refundApplies)) {
            return null;
        }

        // 2.获取最近一次记录，转换vo
        RefundApply apply = refundApplies.get(0);
        if(!Long.valueOf(1).equals(UserContext.getRole()) && !Objects.equals(apply.getUserId(),UserContext.requireUser())) throw new BadRequestException(TradeErrorInfo.REFUND_NOT_EXISTS);
        RefundApplyVO vo = BeanUtils.copyBean(apply, RefundApplyVO.class);

        // 3.查询订单信息
        Order order = orderMapper.getById(apply.getOrderId());
        vo.setPayOrderNo(order.getPayOrderNo());
        vo.setOrderTime(order.getCreateTime());
        vo.setPaySuccessTime(order.getPayTime());

        vo.setPayChannel(PayChannel.desc(order.getPayChannel()));
        vo.setRefundChannel(RefundChannelEnum.desc(apply.getRefundChannel()));
        vo.setRefundOrderNo(apply.getRefundOrderNo());
        return vo;
    }

    @Override
    @Transactional
    public void handleRefundResult(RefundResultDTO result) {
        if(result==null || result.getBizRefundOrderId()==null || result.getBizPayOrderId()==null ||
                (result.getStatus()!=RefundResultDTO.RUNNING && result.getStatus()!=RefundResultDTO.FAILED && result.getStatus()!=RefundResultDTO.SUCCESS))
            throw new BadRequestException("退款结果无效");
        // 1.查询退款申请记录
        RefundApply refundApply = getById(result.getBizRefundOrderId());
        if (refundApply == null) {
            return;
        }
        if(!Objects.equals(refundApply.getOrderId(),result.getBizPayOrderId()))
            throw new com.tianji.common.exceptions.ConflictException("退款结果与订单不匹配");
        jdbc.queryForMap("SELECT id FROM `order` WHERE id=? FOR UPDATE",refundApply.getOrderId());
        refundApply=baseMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RefundApply>().eq("id",refundApply.getId()).last("FOR UPDATE"));
        if(!RefundStatus.AGREE.equalsValue(refundApply.getStatus())) return;
        // 2.判断结果，支付宝支付有可能直接返回退款成功结果，微信只会返回退款中
        RefundApply r = new RefundApply();
        r.setId(refundApply.getId());
        r.setRefundChannel(result.getRefundChannel());
        r.setRefundOrderNo(result.getRefundOrderNo());
        // 2.1.判断状态是否退款中
        int status = result.getStatus();
        if(status == RefundResultDTO.RUNNING){
            // 退款中，结果未知，将其它数据写入数据库即可
            lambdaUpdate().eq(RefundApply::getId,r.getId()).eq(RefundApply::getStatus,3).set(RefundApply::getRefundChannel,r.getRefundChannel()).set(RefundApply::getRefundOrderNo,r.getRefundOrderNo()).update();
            return;
        }

        // 2.2.判断退款成功还是失败
        if(status == RefundResultDTO.SUCCESS){
            // 退款成功，记录状态
            r.setStatus(RefundStatus.SUCCESS.getValue());
            r.setMessage(RefundStatus.SUCCESS.getProgressName());
        }else {
            // 2.3.退款失败，需要记录状态及退款失败原因
            r.setStatus(RefundStatus.FAILED.getValue());
            r.setMessage(RefundStatus.FAILED.getProgressName());
            r.setFailedReason(result.getMsg());
        }

        // 2.4.更新数据库
        r.setFinishTime(LocalDateTime.now());
        boolean changed=lambdaUpdate().eq(RefundApply::getId,r.getId())
                .eq(RefundApply::getStatus,RefundStatus.AGREE.getValue()).set(RefundApply::getStatus,r.getStatus())
                .set(RefundApply::getRefundChannel,r.getRefundChannel()).set(RefundApply::getRefundOrderNo,r.getRefundOrderNo())
                .set(RefundApply::getMessage,r.getMessage()).set(RefundApply::getFailedReason,r.getFailedReason())
                .set(RefundApply::getFinishTime,r.getFinishTime()).update();
        if(!changed) return;

        // 3.更新子订单状态
        detailService.updateRefundStatusById(refundApply.getOrderDetailId(), r.getStatus());
        jdbc.update("UPDATE order_detail SET status=? WHERE id=? AND status=?",status==RefundResultDTO.SUCCESS?OrderStatus.REFUND_FINISHED.getValue():OrderStatus.PAYED.getValue(),refundApply.getOrderDetailId(),OrderStatus.REFUNDED.getValue());

        refreshRefundAggregate(refundApply.getOrderId());

        // 4.如果是退款成功，要取消用户报名的课程
        if (status == RefundResultDTO.SUCCESS) {
            // 4.1.查询子订单信息
            OrderDetail detail = detailService.getById(refundApply.getOrderDetailId());
            // 4.2.发送MQ消息，通知报名成功
            outbox.enqueue("refund:"+refundApply.getId()+":success",
                    MqConstants.Exchange.ORDER_EXCHANGE,
                    MqConstants.Key.ORDER_REFUND_KEY,
                    OrderBasicDTO.builder()
                            .orderId(refundApply.getOrderId())
                            .userId(refundApply.getUserId())
                            .courseIds(CollUtils.singletonList(detail.getCourseId())).detailIds(java.util.Map.of(detail.getCourseId(),detail.getId())).build());
        }
    }

    @Override
    public List<RefundApply> queryApplyToSend(int index, int size) {
        Page<RefundApply> page = lambdaQuery()
                .eq(RefundApply::getStatus, AGREE.getValue())
                .page(new Page<>(index, size));
        if (page == null || CollUtils.isEmpty(page.getRecords())) {
            return CollUtils.emptyList();
        }
        return page.getRecords();
    }

    @Override
    public void sendRefundRequest(RefundApply refundApply) {
        // 1.组织请求参数
        RefundApplyDTO applyDTO = RefundApplyDTO.builder()
                .bizOrderNo(refundApply.getOrderId())
                .bizRefundOrderNo(refundApply.getId())
                .refundAmount(refundApply.getRefundAmount())
                .build();
        // 2.发送退款请求
        RefundResultDTO result = payClient.applyRefund(applyDTO);

        // 3.处理退款结果
        self.getObject().handleRefundResult(result);
    }

    @Override
    public boolean checkRefundStatus(RefundApply refundApply) {
        // 1.先检查是否已经退款成功
        Integer status = refundApply.getStatus();
        if(!AGREE.equalsValue(status)){
            return true;
        }
        // 2.远程查询，判断是否已经退款成功
        RefundResultDTO result = payClient.queryRefundResult(refundApply.getId());
        if (result == null) {
            // 退款数据不存在，放弃处理
            return false;
        }
        // 3.处理退款结果
        self.getObject().handleRefundResult(result);
        return result.getStatus() != RefundResultDTO.RUNNING;
    }

    /** Called with the order locked. Current locking reads avoid stale RR snapshots across detail callbacks. */
    private void refreshRefundAggregate(Long orderId) {
        var rows=jdbc.queryForList("SELECT status,refund_status FROM order_detail WHERE order_id=? FOR UPDATE",orderId);
        boolean allRefunded=!rows.isEmpty() && rows.stream().allMatch(r->Integer.valueOf(7).equals(r.get("status")));
        boolean pending=rows.stream().anyMatch(r->Integer.valueOf(6).equals(r.get("status")) && Set.of(1,3).contains(r.get("refund_status")));
        int target=allRefunded?7:pending?6:2;
        jdbc.update("UPDATE `order` SET status=?,message=? WHERE id=? AND status IN(2,6,7)",target,OrderStatus.desc(target),orderId);
    }
    private void sendRefundRequestAsync(RefundApply refundApply) {
        jdbc.update("INSERT IGNORE INTO refund_delivery_task(refund_id) VALUES(?)",refundApply.getId());
    }
}
