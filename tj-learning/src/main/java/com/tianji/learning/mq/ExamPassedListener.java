package com.tianji.learning.mq;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.common.autoconfigure.reliability.InboxStore;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class ExamPassedListener {
    private final JdbcTemplate jdbc;private final InboxStore inbox;
    private final com.tianji.learning.service.impl.LearningEntitlementService entitlements;
    public record Passed(long attemptId,long userId,long lessonId,long courseId,long sectionId,int sectionCount) {}
    @RabbitListener(bindings=@QueueBinding(value=@Queue(name="learning.exam.passed.queue",durable="true"),
            exchange=@Exchange(name="learning.topic",type=ExchangeTypes.TOPIC),key="exam.passed"))
    public void passed(Passed event,Message raw) {
        if(event.sectionCount()<1) throw new IllegalArgumentException("Invalid examination result");
        inbox.once("learning.exam",raw.getMessageProperties().getMessageId(),()->{
            Long entitled=entitlements.available(event.userId(),event.courseId());
            if(entitled==null || entitled!=event.lessonId()) return;
            var lesson=jdbc.queryForList("SELECT id FROM learning_lesson WHERE id=? AND user_id=? AND course_id=? FOR UPDATE",event.lessonId(),event.userId(),event.courseId());
            if(lesson.isEmpty()) return;
            jdbc.update("INSERT IGNORE INTO learning_record(id,lesson_id,section_id,user_id,moment,finished) VALUES(?,?,?,?,0,0)",IdWorker.getId(),event.lessonId(),event.sectionId(),event.userId());
            if(jdbc.update("UPDATE learning_record SET finished=1,finish_time=CURRENT_TIMESTAMP(3) WHERE lesson_id=? AND section_id=? AND user_id=? AND finished=0",event.lessonId(),event.sectionId(),event.userId())==1)
                jdbc.update("UPDATE learning_lesson SET status=IF(learned_sections+1>=?,2,1),learned_sections=learned_sections+1,latest_section_id=?,latest_learn_time=CURRENT_TIMESTAMP(3) WHERE id=?",event.sectionCount(),event.sectionId(),event.lessonId());
        });
    }
}
