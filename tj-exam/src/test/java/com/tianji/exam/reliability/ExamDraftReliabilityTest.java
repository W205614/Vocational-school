package com.tianji.exam.reliability;

import com.tianji.common.autoconfigure.reliability.OutboxStore;
import com.tianji.common.exceptions.*;
import com.tianji.exam.service.impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class ExamDraftReliabilityTest {
 private JdbcTemplate jdbc;private TransactionTemplate tx;private ExamDraftService drafts;private ExamWorkflowService workflow;
 private long paper,user,question,attempt;
 @BeforeEach void setup(){
  var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_exam?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
  jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));var json=JsonMapper.builder().build();
  paper=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();user=paper+1;question=paper+2;attempt=paper+3;
  var learning=org.mockito.Mockito.mock(com.tianji.api.client.learning.LearningClient.class);org.mockito.Mockito.when(learning.isLessonValid(paper)).thenReturn(paper);
  drafts=new ExamDraftService(jdbc,json);workflow=new ExamWorkflowService(jdbc,json,new OutboxStore(jdbc,json),learning);
  jdbc.update("INSERT INTO exam_paper(id,course_id,section_id,version,total_score,pass_percent,section_count) VALUES(?,?,?,1,10,60,1)",paper,paper,paper);
  jdbc.update("INSERT INTO exam_paper_question(paper_id,question_id,position,name,type,score,answer) VALUES(?,?,1,'objective',2,10,'1,2')",paper,question);
  jdbc.update("INSERT INTO exam_attempt(id,paper_id,user_id,lesson_id) VALUES(?,?,?,?)",attempt,paper,user,paper);
 }
 private ExamDraftService.View save(long version,String answer){return tx.execute(s->drafts.save(attempt,user,new ExamDraftService.Save(version,Map.of(question,answer))));}
 private Object submit(long version){return tx.execute(s->workflow.execute("test",user,JsonMapper.builder().build().writeValueAsString(new ExamWorkflowService.Command("SUBMIT",null,attempt,null,Map.of(question,"1,2"),null,null,version,null,null))));}
 @Test void competingDraftsNeverOverwriteEachOther() throws Exception {
  var accepted=new AtomicInteger();var rejected=new AtomicInteger();
  try(var pool=Executors.newFixedThreadPool(10)){
   var tasks=new ArrayList<Future<?>>();for(int i=0;i<30;i++){final int n=i;tasks.add(pool.submit(()->{try{save(0,"answer-"+n);accepted.incrementAndGet();}catch(ConflictException expected){rejected.incrementAndGet();}}));}
   for(var task:tasks)task.get(30,TimeUnit.SECONDS);
  }
  assertEquals(1,accepted.get());assertEquals(29,rejected.get());
  assertEquals(1L,tx.execute(s->drafts.read(attempt,user)).version());
 }
 @Test void retryAndNewServiceRestoreExactlyTheSameRevision(){
  var first=save(0,"1,2");var retry=save(0,"1,2");assertEquals(first,retry);
  var restarted=new ExamDraftService(jdbc,JsonMapper.builder().build());
  assertEquals(first,tx.execute(s->restarted.read(attempt,user)));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM exam_draft WHERE attempt_id=?",Integer.class,attempt));
 }
 @Test void foreignOwnerAndInvalidQuestionsCannotCreateDraft(){
  assertThrows(BadRequestException.class,()->tx.execute(s->drafts.read(attempt,user+100)));
  assertThrows(BadRequestException.class,()->tx.execute(s->drafts.save(attempt,user+100,new ExamDraftService.Save(0L,Map.of(question,"1")))));
  assertThrows(BadRequestException.class,()->tx.execute(s->drafts.save(attempt,user,new ExamDraftService.Save(0L,Map.of(question+100,"1")))));
  assertThrows(BadRequestException.class,()->save(0,"x".repeat(10001)));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM exam_draft WHERE attempt_id=?",Integer.class,attempt));
 }
 @Test void staleSubmissionAndLateSavesCannotAlterSubmittedAnswers(){
  save(0,"1");save(1,"1,2");
  assertThrows(ConflictException.class,()->submit(1));assertEquals("IN_PROGRESS",jdbc.queryForObject("SELECT status FROM exam_attempt WHERE id=?",String.class,attempt));
  submit(2);assertThrows(ConflictException.class,()->save(2,"3"));
  assertEquals("1,2",jdbc.queryForObject("SELECT response FROM exam_answer WHERE attempt_id=?",String.class,attempt));
  assertEquals(10,jdbc.queryForObject("SELECT score FROM exam_attempt WHERE id=?",Integer.class,attempt));
 }
}
