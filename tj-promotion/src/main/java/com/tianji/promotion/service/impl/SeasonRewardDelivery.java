package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.common.autoconfigure.reliability.OutboxStore;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.promotion.domain.po.*;
import com.tianji.promotion.enums.*;
import com.tianji.promotion.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.*;

/** The receipt, stock update, ticket and acknowledgement share one promotion transaction. */
@Component
public class SeasonRewardDelivery {
 public record Request(String requestId,Long userId,Long couponId,String seasonMonth,Long ruleId) {}
 private final JdbcTemplate jdbc;private final CouponMapper coupons;private final UserCouponMapper tickets;
 private final OutboxStore outbox;private final PlatformTransactionManager manager;
 private final Clock clock;
 @Autowired
 public SeasonRewardDelivery(JdbcTemplate jdbc,CouponMapper coupons,UserCouponMapper tickets,OutboxStore outbox,PlatformTransactionManager manager){
  this(jdbc,coupons,tickets,outbox,manager,Clock.system(ZoneId.of("Asia/Shanghai")));
 }
 SeasonRewardDelivery(JdbcTemplate jdbc,CouponMapper coupons,UserCouponMapper tickets,OutboxStore outbox,PlatformTransactionManager manager,Clock clock){
  this.jdbc=jdbc;this.coupons=coupons;this.tickets=tickets;this.outbox=outbox;this.manager=manager;this.clock=clock;
 }
 @RabbitListener(bindings=@QueueBinding(value=@Queue(value="season.reward.delivery",durable="true"),exchange=@Exchange(value="learning.topic",type="topic"),key="learning.reward.requested"))
 public void receive(Request request,Message raw) {
  if(request.requestId()==null || request.requestId().length()>128 || request.userId()==null || request.couponId()==null || request.ruleId()==null || request.seasonMonth()==null || !request.seasonMonth().matches("[0-9]{6}"))throw new BadRequestException("赛季奖励事件无效");
  String event=Objects.requireNonNull(raw.getMessageProperties().getMessageId(),"Reward event ID required");
  new TransactionTemplate(manager).executeWithoutResult(tx->{
   jdbc.update("INSERT INTO season_reward_delivery(request_id,user_id,coupon_id,season_month,rule_id,status,last_event_id) VALUES(?,?,?,?,?,'PENDING','') ON DUPLICATE KEY UPDATE request_id=request_id",request.requestId(),request.userId(),request.couponId(),request.seasonMonth(),request.ruleId());
   var rows=jdbc.queryForList("SELECT * FROM season_reward_delivery WHERE request_id=? FOR UPDATE",request.requestId());
   if(rows.isEmpty())throw new BadRequestException("奖励唯一标识冲突");
   var receipt=rows.getFirst();
   if(((Number)receipt.get("user_id")).longValue()!=request.userId() || ((Number)receipt.get("coupon_id")).longValue()!=request.couponId() || ((Number)receipt.get("rule_id")).longValue()!=request.ruleId() || !request.seasonMonth().equals(receipt.get("season_month")))throw new BadRequestException("奖励事件内容冲突");
   if("SUCCEEDED".equals(receipt.get("status")))return;
   // Remember every delivery, including failed attempts. A delayed redelivery of an
   // older failure must not be mistaken for a new administrator-requested attempt.
   if(jdbc.update("INSERT IGNORE INTO reliability_inbox(consumer_name,event_id) VALUES('season.reward.delivery',?)",event)==0)return;
   Coupon coupon=coupons.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Coupon>().eq("id",request.couponId()).last("FOR UPDATE"));
   LocalDateTime now=LocalDateTime.now(clock);
   String failure=null;LocalDateTime begin=null,end=null;
   if(coupon==null)failure="优惠券不存在";
   else if(coupon.getStatus()!=CouponStatus.ISSUING || coupon.getIssueBeginTime()==null || coupon.getIssueEndTime()==null || now.isBefore(coupon.getIssueBeginTime()) || !now.isBefore(coupon.getIssueEndTime()))failure="优惠券不在发放期";
   else {
    // These legacy columns have DATETIME(0) precision. Explicitly floor a new
    // relative validity period so MySQL cannot round its start into the future.
    begin=coupon.getTermBeginTime()==null?now.withNano(0):coupon.getTermBeginTime();
    end=coupon.getTermBeginTime()==null?(coupon.getTermDays()==null?null:begin.plusDays(coupon.getTermDays())):coupon.getTermEndTime();
    if(end==null || !end.isAfter(now))failure="优惠券有效期无效";
    else if(jdbc.queryForList("SELECT id FROM user_coupon WHERE user_id=? AND coupon_id=? FOR UPDATE",request.userId(),request.couponId()).size()>=coupon.getUserLimit())failure="已达到个人领取上限";
    else if(coupon.getIssueNum()>=coupon.getTotalNum())failure="优惠券库存不足";
   }
   String ackKey="season-reward:"+UUID.nameUUIDFromBytes((request.requestId()+":"+event).getBytes(java.nio.charset.StandardCharsets.UTF_8));
   if(failure!=null){
    jdbc.update("UPDATE season_reward_delivery SET status='FAILED',last_event_id=?,last_error=? WHERE request_id=?",event,failure,request.requestId());
    outbox.enqueue(ackKey,"learning.topic","learning.reward.failed",Map.of("requestId",request.requestId(),"errorCode","REWARD_UNAVAILABLE","errorMessage",failure));return;
   }
   if(coupons.incrIssueNum(request.couponId())!=1)throw new IllegalStateException("Locked reward stock changed");
   UserCoupon ticket=new UserCoupon().setId(IdWorker.getId()).setUserId(request.userId()).setCouponId(request.couponId()).setTermBeginTime(begin).setTermEndTime(end).setStatus(UserCouponStatus.UNUSED);
   tickets.insert(ticket);
   jdbc.update("UPDATE season_reward_delivery SET status='SUCCEEDED',user_coupon_id=?,last_event_id=?,last_error=NULL WHERE request_id=?",ticket.getId(),event,request.requestId());
   outbox.enqueue(ackKey,"learning.topic","learning.reward.fulfilled",Map.of("requestId",request.requestId(),"userCouponId",ticket.getId()));
  });
 }
}
