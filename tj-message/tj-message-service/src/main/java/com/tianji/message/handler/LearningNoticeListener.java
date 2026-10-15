package com.tianji.message.handler;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.common.autoconfigure.reliability.InboxStore;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.*;
@Component @RequiredArgsConstructor
public class LearningNoticeListener {
 public record Notice(String noticeId,Long userId,String title,String content,Long bizId) {}
 private final InboxStore inbox;private final JdbcTemplate jdbc;
 @RabbitListener(bindings=@QueueBinding(value=@Queue(value="learning.notice.inbox",durable="true"),exchange=@Exchange(value="learning.topic",type="topic"),key="learning.notice.created"))
 public void receive(Notice notice) {
  if(notice.userId()==null || notice.noticeId()==null || notice.title()==null || notice.content()==null)throw new IllegalArgumentException("Invalid learning notice");
  inbox.once("learning.notice",notice.noticeId(),()->{
   LocalDateTime now=LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
   jdbc.update("INSERT INTO user_inbox(id,user_id,type,title,content,is_read,publisher,push_time,expire_time) VALUES(?,?,2,?,?,0,0,?,?)",IdWorker.getId(),notice.userId(),notice.title(),notice.content(),now,now.plusMonths(6));
  });
 }
}
