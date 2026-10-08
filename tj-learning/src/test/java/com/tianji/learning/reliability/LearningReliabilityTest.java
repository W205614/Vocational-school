package com.tianji.learning.reliability;
import com.tianji.common.autoconfigure.reliability.*;
import com.tianji.learning.service.impl.PointsRecordServiceImpl;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.ExamPassedListener;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.amqp.core.*;
import java.util.*;import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class LearningReliabilityTest {
 private JdbcTemplate jdbc;private InboxStore inbox;private long user;
 @BeforeEach void setup(){
  var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_learning?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
  jdbc=new JdbcTemplate(source);inbox=new InboxStore(jdbc,new TransactionTemplate(new DataSourceTransactionManager(source)));user=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
 }
 @Test void concurrentPointsEventsCannotExceedDailyQuotaAndReplayCannotAwardTwice() throws Exception {
  var service=new PointsRecordServiceImpl(jdbc,inbox);String duplicate=UUID.randomUUID().toString();
  service.addPointsRecord(user,7,PointsRecordType.LEARNING,duplicate);
  try(var pool=Executors.newFixedThreadPool(20)){
   var work=new ArrayList<Future<?>>();for(int i=0;i<100;i++){String id=i%2==0?duplicate:UUID.randomUUID().toString();work.add(pool.submit(()->service.addPointsRecord(user,7,PointsRecordType.LEARNING,id)));}
   for(var task:work)task.get(30,TimeUnit.SECONDS);
  }
  assertEquals(50,jdbc.queryForObject("SELECT SUM(points) FROM points_record WHERE user_id=?",Integer.class,user));
  assertEquals(50,jdbc.queryForObject("SELECT points FROM points_daily_quota WHERE user_id=? AND type=1",Integer.class,user));
  assertEquals(50,jdbc.queryForObject("SELECT points FROM points_projection WHERE user_id=?",Integer.class,user));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM points_record WHERE user_id=? AND source_event_id=?",Integer.class,user,duplicate));
 }
 @Test void repeatedExamCompletionCanIncrementLessonOnlyOnceEvenWithDifferentEventIds() throws Exception {
  long lesson=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),section=lesson+1,course=lesson+2;
  jdbc.update("INSERT INTO learning_lesson(id,user_id,course_id,status,learned_sections) VALUES(?,?,?,0,0)",lesson,user,course);
  jdbc.update("INSERT INTO learning_entitlement(order_detail_id,order_id,user_id,course_id,active) VALUES(?,?,?,?,1)",lesson+10,lesson+11,user,course);
  var listener=new ExamPassedListener(jdbc,inbox,new com.tianji.learning.service.impl.LearningEntitlementService(jdbc));var event=new ExamPassedListener.Passed(lesson+3,user,lesson,course,section,1);
  try(var pool=Executors.newFixedThreadPool(20)){
   var work=new ArrayList<Future<?>>();for(int i=0;i<100;i++){String id=UUID.randomUUID().toString();work.add(pool.submit(()->{var properties=new MessageProperties();properties.setMessageId(id);listener.passed(event,new Message(new byte[0],properties));}));}
   for(var task:work)task.get(30,TimeUnit.SECONDS);
  }
  assertEquals(1,jdbc.queryForObject("SELECT learned_sections FROM learning_lesson WHERE id=?",Integer.class,lesson));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_record WHERE lesson_id=? AND finished=1",Integer.class,lesson));
  assertEquals(2,jdbc.queryForObject("SELECT status FROM learning_lesson WHERE id=?",Integer.class,lesson));
 }
 @Test void expiredLessonCannotBeCompletedByDelayedExamEvent(){
  long lesson=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();jdbc.update("INSERT INTO learning_lesson(id,user_id,course_id,status,learned_sections,expire_time) VALUES(?,?,?,0,0,NOW()-INTERVAL 1 DAY)",lesson,user,lesson);
  jdbc.update("INSERT INTO learning_entitlement(order_detail_id,order_id,user_id,course_id,active,expires_at) VALUES(?,?,?,?,1,NOW()-INTERVAL 1 DAY)",lesson+10,lesson+11,user,lesson);
  var properties=new MessageProperties();properties.setMessageId(UUID.randomUUID().toString());new ExamPassedListener(jdbc,inbox,new com.tianji.learning.service.impl.LearningEntitlementService(jdbc)).passed(new ExamPassedListener.Passed(lesson,user,lesson,lesson,lesson,1),new Message(new byte[0],properties));
  assertEquals(0,jdbc.queryForObject("SELECT learned_sections FROM learning_lesson WHERE id=?",Integer.class,lesson));
 }
}
