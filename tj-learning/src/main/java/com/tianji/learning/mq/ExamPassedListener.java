package com.tianji.learning.mq;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.common.autoconfigure.reliability.InboxStore;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class ExamPassedListener {
    private final JdbcTemplate jdbc;private final InboxStore inbox;
    private final com.tianji.learning.service.impl.LearningEntitlementService entitlements;
    private final com.tianji.learning.service.impl.LearningMilestoneService milestones;
    private final java.time.Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public ExamPassedListener(JdbcTemplate jdbc,InboxStore inbox,com.tianji.learning.service.impl.LearningEntitlementService entitlements,com.tianji.learning.service.impl.LearningMilestoneService milestones,@org.springframework.beans.factory.annotation.Qualifier("learningClock") java.time.Clock clock) {
        this.jdbc=jdbc;this.inbox=inbox;this.entitlements=entitlements;this.milestones=milestones;this.clock=clock;
    }
    public ExamPassedListener(JdbcTemplate jdbc,InboxStore inbox,com.tianji.learning.service.impl.LearningEntitlementService entitlements) {
        this(jdbc,inbox,entitlements,null,java.time.Clock.system(java.time.ZoneId.of("Asia/Shanghai")));
    }
    public record Passed(long attemptId,long userId,long lessonId,long courseId,long sectionId,int sectionCount,java.time.LocalDateTime occurredAt) {
        public Passed(long attemptId,long userId,long lessonId,long courseId,long sectionId,int sectionCount){this(attemptId,userId,lessonId,courseId,sectionId,sectionCount,null);}
    }
    @RabbitListener(bindings=@QueueBinding(value=@Queue(name="learning.exam.passed.queue",durable="true"),
            exchange=@Exchange(name="learning.topic",type=ExchangeTypes.TOPIC),key="exam.passed"))
    public void passed(Passed event,Message raw) {
        if(event.sectionCount()<1) throw new IllegalArgumentException("Invalid examination result");
        inbox.once("learning.exam",raw.getMessageProperties().getMessageId(),()->{
            Long entitled=entitlements.available(event.userId(),event.courseId());
            if(entitled==null || entitled!=event.lessonId()) return;
            var lesson=jdbc.queryForList("SELECT id FROM learning_lesson WHERE id=? AND user_id=? AND course_id=? FOR UPDATE",event.lessonId(),event.userId(),event.courseId());
            if(lesson.isEmpty()) return;
            java.time.LocalDateTime completedAt=milestones==null && event.occurredAt()==null?java.time.LocalDateTime.now(clock):businessTime(event,raw);
            jdbc.update("INSERT IGNORE INTO learning_record(id,lesson_id,section_id,user_id,moment,finished) VALUES(?,?,?,?,0,0)",IdWorker.getId(),event.lessonId(),event.sectionId(),event.userId());
            if(jdbc.update("UPDATE learning_record SET finished=1,finish_time=? WHERE lesson_id=? AND section_id=? AND user_id=? AND finished=0",completedAt,event.lessonId(),event.sectionId(),event.userId())==1) {
                jdbc.update("UPDATE learning_lesson SET status=IF(learned_sections+1>=?,2,1),learned_sections=learned_sections+1,latest_section_id=IF(latest_learn_time IS NULL OR latest_learn_time<=?,?,latest_section_id),latest_learn_time=GREATEST(COALESCE(latest_learn_time,?),?) WHERE id=?",event.sectionCount(),completedAt,event.sectionId(),completedAt,completedAt,event.lessonId());
                if(milestones!=null)milestones.completed(event.userId(),event.courseId(),event.sectionId(),true,completedAt);
            }
        });
    }
    private java.time.LocalDateTime businessTime(Passed event,Message raw) {
        if(event.occurredAt()!=null)return event.occurredAt();
        var envelope=tools.jackson.databind.json.JsonMapper.builder().build().readTree(raw.getBody());
        if(!envelope.has("occurredAt"))throw new IllegalArgumentException("旧考试事件缺少可核实时间，需要人工核对");
        return java.time.Instant.parse(envelope.get("occurredAt").asString()).atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();
    }
}
