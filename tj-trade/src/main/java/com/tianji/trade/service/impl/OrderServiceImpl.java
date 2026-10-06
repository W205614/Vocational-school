package com.tianji.trade.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.promotion.PromotionClient;
import com.tianji.api.constants.CourseStatus;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCouponDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.pay.sdk.dto.PayResultDTO;
import com.tianji.trade.config.TradeProperties;
import com.tianji.trade.constants.OrderStatus;
import com.tianji.trade.constants.RefundStatus;
import com.tianji.trade.constants.TradeErrorInfo;
import com.tianji.trade.domain.dto.PlaceOrderDTO;
import com.tianji.trade.domain.po.Order;
import com.tianji.trade.domain.po.OrderDetail;
import com.tianji.trade.domain.query.OrderPageQuery;
import com.tianji.trade.domain.vo.*;
import com.tianji.trade.mapper.OrderMapper;
import com.tianji.trade.service.ICartService;
import com.tianji.trade.service.IOrderDetailService;
import com.tianji.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.tianji.common.constants.ErrorInfo.Msg.OPERATE_FAILED;
import static com.tianji.trade.constants.TradeErrorInfo.ORDER_ALREADY_FINISH;
import static com.tianji.trade.constants.TradeErrorInfo.ORDER_NOT_EXISTS;

/**
 * <p>
 * 订单 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-29
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private final CourseClient courseClient;
    private final IOrderDetailService detailService;
    private final ICartService cartService;
    private final TradeProperties tradeProperties;
    private final RabbitMqHelper rabbitMqHelper;
    private final PromotionClient promotionClient;
    private final com.tianji.common.autoconfigure.reliability.OutboxStore outbox;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;
    private final tools.jackson.databind.json.JsonMapper json;

    @Override
    public PlaceOrderResultVO placeOrder(PlaceOrderDTO request) {
        long user=UserContext.requireUser();
        if(request.getOrderId()==null || request.getCourseIds()==null || request.getCourseIds().isEmpty() || request.getCourseIds().size()>10 ||
                request.getCourseIds().stream().anyMatch(Objects::isNull) || new java.util.HashSet<>(request.getCourseIds()).size()!=request.getCourseIds().size())
            throw new BadRequestException("订单课程无效");
        String payload=json.writeValueAsString(request),hash=cn.hutool.crypto.digest.DigestUtil.sha256Hex(user+":"+payload);
        LocalDateTime deadline=LocalDateTime.now().plusMinutes(tradeProperties.getPayOrderTTLMinutes());
        jdbc.update("INSERT IGNORE INTO order_creation(order_id,user_id,request_hash,payload,expires_at) VALUES(?,?,?,?,?)",request.getOrderId(),user,hash,payload,deadline);
        var creation=jdbc.queryForMap("SELECT * FROM order_creation WHERE order_id=?",request.getOrderId());
        if(!hash.equals(creation.get("request_hash"))) throw new com.tianji.common.exceptions.ConflictException("订单标识已用于不同请求");
        Order existing=getById(request.getOrderId());
        if(existing!=null) return queryOrderStatus(request.getOrderId());
        deadline=com.tianji.common.utils.JdbcTime.localDateTime(creation.get("expires_at"));
        if(!"ACTIVE".equals(creation.get("status")) || !deadline.isAfter(LocalDateTime.now())) throw new BadRequestException("订单创建已超时，请重新确认");
        List<CourseSimpleInfoDTO> courseInfos=getOnShelfCourse(request.getCourseIds());
        if(courseInfos==null || courseInfos.size()!=request.getCourseIds().size()) throw new BadRequestException("课程信息不完整");
        long sum=courseInfos.stream().mapToLong(CourseSimpleInfoDTO::getPrice).sum();
        if(sum<0 || sum>Integer.MAX_VALUE) throw new BadRequestException("订单金额无效");
        Order order=new Order().setId(request.getOrderId()).setUserId(user).setTotalAmount((int)sum).setDiscountAmount(0)
                .setStatus(OrderStatus.NO_PAY.getValue()).setMessage(OrderStatus.NO_PAY.getProgressName());
        CouponDiscountDTO discount=null;
        if(CollUtils.isNotEmpty(request.getCouponIds())) {
            List<OrderCourseDTO> courses=courseInfos.stream().map(c->new OrderCourseDTO().setId(c.getId()).setCateId(c.getThirdCateId()).setPrice(c.getPrice())).toList();
            discount=promotionClient.queryDiscountDetailByOrder(new OrderCouponDTO(request.getCouponIds(),courses,request.getOrderId()));
            if(discount==null) throw new com.tianji.common.exceptions.CommonException("优惠计算未完成，请重试");
            order.setDiscountAmount(discount.getDiscountAmount());order.setCouponIds(discount.getIds());
            promotionClient.reserveCoupons(order.getId(),new com.tianji.api.dto.promotion.CouponReservationDTO(user,request.getCouponIds(),deadline));
        }
        order.setRealAmount(order.getTotalAmount()-order.getDiscountAmount());
        if(order.getRealAmount()<0) throw new BadRequestException("折扣分摊无效");
        order.setCreateTime(com.tianji.common.utils.JdbcTime.localDateTime(creation.get("created_at")));
        List<OrderDetail> details=new ArrayList<>();
        for(var course:courseInfos) details.add(packageOrderDetail(course,order,discount==null?0:discount.getDiscountDetail().getOrDefault(course.getId(),0)));
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            var locked=jdbc.queryForMap("SELECT status,expires_at FROM order_creation WHERE order_id=? FOR UPDATE",order.getId());
            if(!"ACTIVE".equals(locked.get("status")) || !com.tianji.common.utils.JdbcTime.localDateTime(locked.get("expires_at")).isAfter(LocalDateTime.now()))
                throw new BadRequestException("订单创建已超时");
            if(getById(order.getId())!=null) return;
            saveOrderAndDetails(order,details);
            cartService.deleteCartByUserAndCourseIds(user,request.getCourseIds());
            jdbc.update("UPDATE order_creation SET status='CREATED' WHERE order_id=?",order.getId());
        });
        return queryOrderStatus(order.getId());
    }

    private List<CourseSimpleInfoDTO> getOnShelfCourse(List<Long> courseIds) {
        // 1.查询课程
        List<CourseSimpleInfoDTO> courseInfos = courseClient.getSimpleInfoList(courseIds);
        LocalDateTime now = LocalDateTime.now();
        // 2.判断状态
        for (CourseSimpleInfoDTO courseInfo : courseInfos) {
            // 2.1.检查课程是否上架
            if(!CourseStatus.SHELF.equalsValue(courseInfo.getStatus())){
                throw new BizIllegalException(TradeErrorInfo.COURSE_NOT_FOR_SALE);
            }
            // 2.2.检查课程是否过期
            if(courseInfo.getPurchaseEndTime()!=null && courseInfo.getPurchaseEndTime().isBefore(now)){
                throw new BizIllegalException(TradeErrorInfo.COURSE_EXPIRED);
            }
        }
        return courseInfos;
    }


    @Override
    public PlaceOrderResultVO enrolledFreeCourse(Long courseId) {
        Long userId = UserContext.requireUser();
        jdbc.update("INSERT INTO free_enrollment(user_id,course_id,order_id) VALUES(?,?,?) ON DUPLICATE KEY UPDATE order_id=order_id",userId,courseId,IdWorker.getId());
        long orderId=jdbc.queryForObject("SELECT order_id FROM free_enrollment WHERE user_id=? AND course_id=?",Long.class,userId,courseId);
        if(getById(orderId)!=null)return queryOrderStatus(orderId);
        // 1.查询课程信息
        List<Long> cIds = CollUtils.singletonList(courseId);
        List<CourseSimpleInfoDTO> courseInfos = getOnShelfCourse(cIds);
        if (CollUtils.isEmpty(courseInfos)) {
            // 课程不存在
            throw new BizIllegalException(TradeErrorInfo.COURSE_NOT_EXISTS);
        }
        CourseSimpleInfoDTO courseInfo = courseInfos.get(0);
        if(!courseInfo.getFree()){
            // 非免费课程，直接报错
            throw new BizIllegalException(TradeErrorInfo.COURSE_NOT_FREE);
        }
        // Metadata calls finish before the local transaction. The durable row
        // gives all retries and concurrent requests the same order identity.
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(tx->{
        jdbc.queryForMap("SELECT order_id FROM free_enrollment WHERE user_id=? AND course_id=? FOR UPDATE",userId,courseId);
        if(getById(orderId)!=null)return queryOrderStatus(orderId);
        // 2.创建订单
        Order order = new Order();
        // 2.1.基本信息
        order.setUserId(userId);
        order.setTotalAmount(0);
        order.setDiscountAmount(0);
        order.setRealAmount(0);
        order.setStatus(OrderStatus.ENROLLED.getValue());
        order.setFinishTime(LocalDateTime.now());
        order.setMessage(OrderStatus.ENROLLED.getProgressName());
        // 2.2.订单id
        order.setId(orderId);

        // 3.订单详情
        OrderDetail detail = packageOrderDetail(courseInfo, order, 0);

        // 4.写入数据库
        saveOrderAndDetails(order, CollUtils.singletonList(detail));

        // 5.发送MQ消息，通知报名成功
        outbox.enqueue("order:"+orderId+":enrolled",
                MqConstants.Exchange.ORDER_EXCHANGE,
                MqConstants.Key.ORDER_PAY_KEY,
                OrderBasicDTO.builder()
                        .orderId(orderId)
                        .userId(userId)
                        .courseIds(cIds)
                        .detailIds(Map.of(courseId,detail.getId()))
                        .finishTime(order.getFinishTime())
                        .build()
        );
        // 6.返回vo
        return PlaceOrderResultVO.builder()
                .orderId(orderId)
                .payAmount(0)
                .status(order.getStatus())
                .build();
        });
    }

    @Override
    public OrderConfirmVO prePlaceOrder(List<Long> courseIds) {
        // 1.查询课程信息
        List<CourseSimpleInfoDTO> courseInfos = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(courseInfos)) {
            throw new BizIllegalException(TradeErrorInfo.COURSE_NOT_EXISTS);
        }
        List<OrderCourseVO> courses = BeanUtils.copyList(courseInfos, OrderCourseVO.class);
        // 2.计算总价
        int total = courseInfos.stream().mapToInt(CourseSimpleInfoDTO::getPrice).sum();
        // 3.计算折扣
        List<OrderCourseDTO> orderCourse = courseInfos.stream()
                .map(ci -> new OrderCourseDTO().setId(ci.getId()).setCateId(ci.getThirdCateId()).setPrice(ci.getPrice()))
                .collect(Collectors.toList());
        List<CouponDiscountDTO> discountSolution = promotionClient.findDiscountSolution(orderCourse);
        // 4.生成订单id
        long orderId = IdWorker.getId();
        // 5.组织返回
        OrderConfirmVO vo = new OrderConfirmVO();
        vo.setOrderId(orderId);
        vo.setTotalAmount(total);
        vo.setDiscounts(discountSolution);
        vo.setCourses(courses);
        return vo;
    }

    private OrderDetail packageOrderDetail(CourseSimpleInfoDTO courseInfo, Order order, Integer discountValue) {
        OrderDetail detail = new OrderDetail();
        detail.setUserId(order.getUserId());
        detail.setOrderId(order.getId());
        detail.setStatus(order.getStatus());
        detail.setCourseId(courseInfo.getId());
        detail.setPrice(courseInfo.getPrice());
        detail.setCoverUrl(courseInfo.getCoverUrl());
        detail.setName(courseInfo.getName());
        detail.setValidDuration(courseInfo.getValidDuration());
        detail.setDiscountAmount(discountValue);
        detail.setRealPayAmount(courseInfo.getPrice() - detail.getDiscountAmount());
        return detail;
    }

    @Override
    @Transactional
    public void saveOrderAndDetails(Order order, List<OrderDetail> orderDetails) {
        // 4.1.写订单
        boolean success = save(order);
        if (!success) {
            throw new DbException(TradeErrorInfo.PLACE_ORDER_FAILED);
        }
        // 4.2.写订单详情
        if(orderDetails.size() == 1){
            success = detailService.save(orderDetails.get(0));
        }else {
            success = detailService.saveBatch(orderDetails);
        }
        if (!success) {
            throw new DbException(TradeErrorInfo.PLACE_ORDER_FAILED);
        }
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId) {
        Long userId = UserContext.getUser();
        // 1.查询订单
        Order order = getById(orderId);
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BadRequestException(ORDER_NOT_EXISTS);
        }
        // 2.判断订单状态是否已经取消，幂等判断
        if(OrderStatus.CLOSED.equalsValue(order.getStatus())){
           // 订单已经取消，无需重复操作
           return;
        }
        // 3.判断订单是否未支付，只有未支付订单才可以取消
        if(!OrderStatus.NO_PAY.equalsValue(order.getStatus())){
            throw new BizIllegalException(ORDER_ALREADY_FINISH);
        }
        // 4.可以更新订单状态为取消了
        boolean success = lambdaUpdate()
                .set(Order::getStatus, OrderStatus.CLOSED.getValue())
                .set(Order::getMessage, "用户取消订单")
                .set(Order::getCloseTime, LocalDateTime.now())
                .eq(Order::getStatus, OrderStatus.NO_PAY.getValue())
                .eq(Order::getId, orderId)
                .update();
        if (!success) {
            return;
        }
        // 5.更新订单条目的状态
        detailService.updateStatusByOrderId(orderId, OrderStatus.CLOSED.getValue());
        // 6. 退还优惠券
        outbox.enqueue("order:"+orderId+":coupon-release",MqConstants.Exchange.PROMOTION_EXCHANGE,"coupon.reservation.release",
                Map.of("orderId",orderId,"userId",order.getUserId()));
    }

    @Override
    public void deleteOrder(Long id) {
        // 1.获取登录用户
        Long userId = UserContext.getUser();
        // 2.查询订单
        Order order = getById(id);
        if (order == null) {
            return;
        }
        // 3.判断订单所属用户与当前登录用户是否一致
        if(!Objects.equals(userId, order.getUserId())){
            // 不一致，说明不是当前用户的订单，结束
            throw new BadRequestException("不能删除他人订单");
        }
        // 4.删除订单
        boolean success = removeById(id);
        if (!success) {
            throw new DbException(OPERATE_FAILED);
        }
    }

    @Override
    public PageDTO<OrderPageVO> queryMyOrderPage(OrderPageQuery pageQuery) {
        Long userId = UserContext.getUser();
        // 1.分页排序条件
        Page<Order> p = pageQuery.toMpPageDefaultSortByCreateTimeDesc();
        // 2.分页查询订单
        Integer status = pageQuery.getStatus();
        Page<Order> page = lambdaQuery()
                .eq(status != null, Order::getStatus, status)
                .eq(Order::getUserId, userId)
                .page(p);
        // 3.数据判断
        List<Order> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(p);
        }
        // 4.查询订单明细信息
        List<Long> orderIds = records.stream().map(Order::getId).collect(Collectors.toList());
        // 4.1.根据订单id查询订单明细
        List<OrderDetail> details = detailService.queryByOrderIds(orderIds);
        // 4.2.将订单明细分组，key是订单id，值是订单下的所有detail
        Map<Long, List<OrderDetailVO>> detailMap = details.stream()
                .map(od -> BeanUtils.copyBean(od, OrderDetailVO.class))
                .collect(Collectors.groupingBy(OrderDetailVO::getOrderId));
        // 5.转换VO
        List<OrderPageVO> list = new ArrayList<>(orderIds.size());
        for (Order record : records) {
            // 5.1.转换订单
            OrderPageVO v = BeanUtils.toBean(record, OrderPageVO.class);
            list.add(v);
            // 5.2.写入vo
            v.setDetails(detailMap.get(record.getId()));
            v.setStatusDesc(OrderStatus.desc(v.getStatus()));
        }
        return PageDTO.of(page, list);
    }

    @Override
    public OrderVO queryOrderById(Long id) {
        // 1.查询订单
        Order order = getById(id);
        if (order == null || !Objects.equals(order.getUserId(), UserContext.requireUser())) {
            throw new BadRequestException(ORDER_NOT_EXISTS);
        }
        // 2.查询订单详情
        List<OrderDetail> details = detailService.queryByOrderId(id);
        // 3.转换VO
        // 3.1.订单
        OrderVO vo = BeanUtils.toBean(order, OrderVO.class);
        // 3.2.订单详情
        List<OrderDetailVO> dvs = BeanUtils.copyList(details, OrderDetailVO.class, (d, v) -> v.setCanRefund(
                // 订单已经支付，且 退款没有在进行中，标记为可退款状态
                OrderStatus.canRefund(d.getStatus()) && !RefundStatus.inProgress(v.getRefundStatus())
        ));
        vo.setDetails(dvs);
        // 3.3.订单进度
        vo.setProgressNodes(detailService.packageProgressNodes(order, null));
        // 3.4 优惠明细
        List<String> rules = CollUtils.isEmpty(order.getCouponIds())?List.of():promotionClient.queryDiscountRules(order.getCouponIds());
        vo.setCouponDesc(String.join("/", rules));
        return vo;
    }

    @Override
    public PlaceOrderResultVO queryOrderStatus(Long orderId) {
        // 1.查询订单
        Order order = getById(orderId);
        if (order == null || !Objects.equals(order.getUserId(), UserContext.requireUser())) {
            throw new BizIllegalException(ORDER_NOT_EXISTS);
        }
        // 2.计算超时时间
        LocalDateTime outTime = null;
        if(OrderStatus.NO_PAY.equalsValue(order.getStatus())){
            outTime = order.getCreateTime().plusMinutes(tradeProperties.getPayOrderTTLMinutes());
        }
        // 3.封装结果
        return PlaceOrderResultVO.builder()
                .orderId(orderId)
                .payAmount(order.getRealAmount())
                .status(order.getStatus())
                .payOutTime(outTime)
                .build();
    }

    @Override
    @Transactional
    public void handlePaySuccess(PayResultDTO payResult) {
        if(payResult==null || payResult.getStatus()!=PayResultDTO.SUCCESS || payResult.getPayOrderNo()==null || payResult.getBizOrderId()==null || payResult.getSuccessTime()==null || payResult.getPayChannel()==null)
            throw new BadRequestException("无效的支付成功事实");
        Long id=payResult.getBizOrderId();
        jdbc.update("INSERT IGNORE INTO payment_fact(pay_order_no,order_id,pay_channel,paid_at) VALUES(?,?,?,?)",
                payResult.getPayOrderNo(),id,payResult.getPayChannel(),payResult.getSuccessTime());
        var fact=jdbc.queryForMap("SELECT order_id FROM payment_fact WHERE pay_order_no=?",payResult.getPayOrderNo());
        if(!Objects.equals(((Number)fact.get("order_id")).longValue(),id))
            throw new BadRequestException("支付流水关联订单冲突");
        Order order=baseMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Order>().eq("id",id).last("FOR UPDATE"));
        if(order==null || !OrderStatus.NO_PAY.equalsValue(order.getStatus())) {
            if(order!=null && Objects.equals(order.getPayOrderNo(),payResult.getPayOrderNo())) return;
            jdbc.update("INSERT IGNORE INTO payment_conflict(order_id,pay_order_no,reason,status) VALUES(?,?,?,'OPEN')",
                    id,payResult.getPayOrderNo(),order==null?"UNKNOWN_ORDER":"PAYMENT_AFTER_STATE_CHANGE");
            return;
        }
        boolean changed=lambdaUpdate().eq(Order::getId,id).eq(Order::getStatus,OrderStatus.NO_PAY.getValue())
                .set(Order::getStatus,OrderStatus.PAYED.getValue()).set(Order::getPayTime,payResult.getSuccessTime())
                .set(Order::getPayChannel,payResult.getPayChannel()).set(Order::getPayOrderNo,payResult.getPayOrderNo())
                .set(Order::getMessage,"用户支付成功").update();
        if(!changed) throw new IllegalStateException("Order transition raced");
        detailService.markDetailSuccessByOrderId(id,payResult.getPayChannel(),payResult.getSuccessTime());
        List<Long> courseIds=detailService.queryCourseIdsByOrderId(id);
        outbox.enqueue("order:"+id+":paid",MqConstants.Exchange.ORDER_EXCHANGE,MqConstants.Key.ORDER_PAY_KEY,
                OrderBasicDTO.builder().orderId(id).userId(order.getUserId()).courseIds(courseIds).detailIds(detailService.queryByOrderId(id).stream().collect(Collectors.toMap(OrderDetail::getCourseId,OrderDetail::getId))).finishTime(payResult.getSuccessTime()).build());
    }
}
