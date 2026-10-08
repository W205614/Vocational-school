package com.tianji.exam.reliability;
import com.tianji.common.autoconfigure.reliability.*;
import com.tianji.common.exceptions.*;
import com.tianji.exam.service.impl.ExamWorkflowService;
import com.tianji.exam.service.impl.ExamWorkflowService.Command;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.*;import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class ExamReliabilityTest {
 private JdbcTemplate jdbc;private TransactionTemplate tx;private ExamWorkflowService service;private JsonMapper json;
 private long paper,user,question,teacher;
 private com.tianji.api.client.learning.LearningClient learning;
 @BeforeEach void setup(){
  var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_exam?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
  jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));json=JsonMapper.builder().build();
  learning=org.mockito.Mockito.mock(com.tianji.api.client.learning.LearningClient.class);
  service=new ExamWorkflowService(jdbc,json,new OutboxStore(jdbc,json),learning);paper=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();user=paper+1;question=paper+2;teacher=paper+3;
  org.mockito.Mockito.when(learning.isLessonValid(paper)).thenReturn(paper);
  jdbc.update("INSERT INTO exam_paper(id,course_id,section_id,version,total_score,pass_percent,section_count) VALUES(?,?,?,1,10,60,1)",paper,paper,paper);
 }
 private Map<String,Object> execute(long actor,Command command){return tx.execute(s->(Map<String,Object>)service.execute(UUID.randomUUID().toString(),actor,json.writeValueAsString(command)));}
 private long start(){var view=execute(user,new Command("START",paper,null,paper,null,null,null,null,null,null));return ((Number)view.get("id")).longValue();}
 private com.tianji.exam.controller.ExamWorkflowController controller(){return new com.tianji.exam.controller.ExamWorkflowController(service,null,null,learning,jdbc,null);}
 @Test void historyIsOwnerScopedPagedAndReadableAfterEntitlementRevocation(){
  long otherPaper=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
  jdbc.update("INSERT INTO exam_paper(id,course_id,section_id,version,total_score,pass_percent,section_count) VALUES(?,?,?,1,10,60,1)",otherPaper,otherPaper,otherPaper);
  List<Long> mine=new ArrayList<>();
  for(int i=0;i<3;i++){long id=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();mine.add(id);jdbc.update("INSERT INTO exam_attempt(id,paper_id,user_id,lesson_id,status,score,passed,created_at) VALUES(?,?,?,?,'FINISHED',8,1,'2026-01-01 10:00:00')",id,paper,user,paper);}
  long foreign=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),other=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
  jdbc.update("INSERT INTO exam_attempt(id,paper_id,user_id,lesson_id,status,score,passed,created_at) VALUES(?,?,?,?,'FINISHED',8,1,'2026-01-02 10:00:00')",foreign,paper,user+99,paper);
  jdbc.update("INSERT INTO exam_attempt(id,paper_id,user_id,lesson_id,status) VALUES(?,?,?,?,'FINISHED')",other,otherPaper,user,otherPaper);
  jdbc.update("INSERT INTO exam_paper_question(paper_id,question_id,position,name,type,score,answer,analysis) VALUES(?,?,1,'retained objective',2,10,'1,2','private reference')",paper,question);
  jdbc.update("INSERT INTO exam_answer(attempt_id,question_id,response,score) VALUES(?,?,?,8)",mine.getLast(),question,"2,1");
  org.mockito.Mockito.reset(learning);org.mockito.Mockito.when(learning.isLessonValid(paper)).thenReturn(null);
  com.tianji.common.utils.UserContext.setUser(user);
  try{
   var first=controller().history(1,2,paper);assertEquals(3L,first.get("total"));assertEquals(1,first.get("pageNo"));
   var rows=(List<Map<String,Object>>)first.get("list");assertEquals(List.of(mine.get(2),mine.get(1)),rows.stream().map(r->r.get("id")).toList());
   for(var row:rows){assertFalse(row.containsKey("user_id"));assertFalse(row.containsKey("answers"));assertFalse(row.containsKey("answer"));assertFalse(row.containsKey("analysis"));}
   assertEquals(mine.getFirst(),((List<Map<String,Object>>)controller().history(2,2,paper).get("list")).getFirst().get("id"));
   assertTrue(((List<?>)controller().history(3,2,paper).get("list")).isEmpty());assertEquals(4L,controller().history(1,20,null).get("total"));assertEquals(1L,controller().history(1,20,otherPaper).get("total"));
   var detail=controller().attempt(mine.getLast());assertTrue(json.writeValueAsString(detail).contains("2,1"));assertFalse(json.writeValueAsString(detail).contains("private reference"));
   assertThrows(BadRequestException.class,()->controller().attempt(foreign));org.mockito.Mockito.verifyNoInteractions(learning);
   assertThrows(ForbiddenException.class,this::start);
  }finally{com.tianji.common.utils.UserContext.removeUser();}
 }
 @Test void historyRejectsAnonymousAndUnboundedOrInvalidQueries(){
  com.tianji.common.utils.UserContext.removeUser();assertThrows(UnauthorizedException.class,()->controller().history(1,20,null));
  com.tianji.common.utils.UserContext.setUser(user);
  try{for(int[] range:List.of(new int[]{0,20},new int[]{10001,20},new int[]{1,0},new int[]{1,101}))assertThrows(BadRequestException.class,()->controller().history(range[0],range[1],null));assertThrows(BadRequestException.class,()->controller().history(1,20,0L));assertThrows(BadRequestException.class,()->controller().history(1,20,-1L));assertEquals(0L,controller().history(1,20,paper+999).get("total"));}
  finally{com.tianji.common.utils.UserContext.removeUser();}
 }
 @Test void workerUsesDurableOwnerAndRestoresIdentityAfterFailedValidation() {
  var oldUser=user+100;var oldSession="previous-request";
  com.tianji.common.utils.UserContext.setUser(oldUser);com.tianji.common.utils.UserContext.setRole(1L);
  com.tianji.common.utils.UserContext.setSession(oldSession);com.tianji.common.utils.UserContext.setCallDepth(3);
  try {
   org.mockito.Mockito.when(learning.isLessonValid(paper)).thenAnswer(call->{
    assertEquals(user,com.tianji.common.utils.UserContext.getUser());
    assertEquals(2L,com.tianji.common.utils.UserContext.getRole());
    assertNull(com.tianji.common.utils.UserContext.getSession());
    assertEquals(0,com.tianji.common.utils.UserContext.getCallDepth());
    throw new com.tianji.common.exceptions.ForbiddenException("revoked");
   });
   assertThrows(ForbiddenException.class,this::start);
   assertEquals(oldUser,com.tianji.common.utils.UserContext.getUser());
   assertEquals(1L,com.tianji.common.utils.UserContext.getRole());
   assertEquals(oldSession,com.tianji.common.utils.UserContext.getSession());
   assertEquals(3,com.tianji.common.utils.UserContext.getCallDepth());
   assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM exam_attempt WHERE paper_id=?",Integer.class,paper));
  } finally {com.tianji.common.utils.UserContext.removeUser();}
 }
 @Test void queuedStartAndSubmissionRecheckRevokedEntitlement() {
  org.mockito.Mockito.when(learning.isLessonValid(paper)).thenReturn(null);
  assertThrows(ForbiddenException.class,this::start);assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM exam_attempt WHERE paper_id=?",Integer.class,paper));
  org.mockito.Mockito.when(learning.isLessonValid(paper)).thenReturn(paper);long attempt=start();
  org.mockito.Mockito.when(learning.isLessonValid(paper)).thenReturn(null);
  assertThrows(ForbiddenException.class,()->execute(user,new Command("SUBMIT",null,attempt,null,Map.of(),null,null,null,null,null)));
  assertEquals("IN_PROGRESS",jdbc.queryForObject("SELECT status FROM exam_attempt WHERE id=?",String.class,attempt));
 }
 @Test void concurrentStartsReturnOneActiveAttempt() throws Exception{
  jdbc.update("INSERT INTO exam_paper_question(paper_id,question_id,position,name,type,score,answer) VALUES(?,?,1,'objective',2,10,'1,2')",paper,question);
  Set<Long> attempts=ConcurrentHashMap.newKeySet();try(var pool=Executors.newFixedThreadPool(20)){List<Future<?>> work=new ArrayList<>();for(int i=0;i<100;i++)work.add(pool.submit(()->attempts.add(start())));for(var task:work)task.get(30,TimeUnit.SECONDS);}
  assertEquals(1,attempts.size());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM exam_attempt WHERE paper_id=?",Integer.class,paper));
 }
 @Test void duplicateSubmissionsPreserveAnswersAndEmitOnePassedEvent() throws Exception{
  jdbc.update("INSERT INTO exam_paper_question(paper_id,question_id,position,name,type,score,answer,analysis) VALUES(?,?,1,'objective',2,10,'1,2','private answer explanation')",paper,question);
  long attempt=start();var command=new Command("SUBMIT",null,attempt,null,Map.of(question,"2,1"),null,null,null,null,null);
  try(var pool=Executors.newFixedThreadPool(20)){List<Future<?>> work=new ArrayList<>();for(int i=0;i<100;i++)work.add(pool.submit(()->execute(user,command)));for(var task:work)task.get(30,TimeUnit.SECONDS);}
  execute(user,new Command("SUBMIT",null,attempt,null,Map.of(question,"3"),null,null,null,null,null));
  assertEquals("2,1",jdbc.queryForObject("SELECT response FROM exam_answer WHERE attempt_id=?",String.class,attempt));
  assertEquals(10,jdbc.queryForObject("SELECT score FROM exam_attempt WHERE id=?",Integer.class,attempt));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM reliability_outbox WHERE business_key=?",Integer.class,"exam:"+attempt+":passed"));
  var view=service.view(attempt,user,false);assertFalse(json.writeValueAsString(view).contains("private answer explanation"));
  assertThrows(BadRequestException.class,()->service.view(attempt,user+99,false));
 }
 @Test void concurrentSubjectiveGradesUseVersionAndFinishOnlyOnce() throws Exception{
  jdbc.update("INSERT INTO exam_paper_question(paper_id,question_id,position,name,type,score) VALUES(?,?,1,'subjective',5,10)",paper,question);
  jdbc.update("INSERT INTO exam_grader(paper_id,user_id) VALUES(?,?)",paper,teacher);
  long attempt=start();execute(user,new Command("SUBMIT",null,attempt,null,Map.of(question,"my answer"),null,null,null,null,null));
  AtomicInteger accepted=new AtomicInteger(),conflicted=new AtomicInteger();var command=new Command("GRADE",null,attempt,null,null,question,8,0L,"reviewed",3L);
  try(var pool=Executors.newFixedThreadPool(20)){List<Future<?>> work=new ArrayList<>();for(int i=0;i<50;i++)work.add(pool.submit(()->{try{execute(teacher,command);accepted.incrementAndGet();}catch(ConflictException expected){conflicted.incrementAndGet();}}));for(var task:work)task.get(30,TimeUnit.SECONDS);}
  assertEquals(1,accepted.get());assertEquals(49,conflicted.get());assertEquals("FINISHED",jdbc.queryForObject("SELECT status FROM exam_attempt WHERE id=?",String.class,attempt));
  assertEquals(8,jdbc.queryForObject("SELECT score FROM exam_attempt WHERE id=?",Integer.class,attempt));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM reliability_outbox WHERE business_key=?",Integer.class,"exam:"+attempt+":passed"));
 }
 @Test void unauthorizedTeacherAndAnswersOutsidePaperCannotChangeAttempt(){
  jdbc.update("INSERT INTO exam_paper_question(paper_id,question_id,position,name,type,score) VALUES(?,?,1,'subjective',5,10)",paper,question);
  long attempt=start();assertThrows(BadRequestException.class,()->execute(user,new Command("SUBMIT",null,attempt,null,Map.of(question+100,"bad"),null,null,null,null,null)));
  assertEquals("IN_PROGRESS",jdbc.queryForObject("SELECT status FROM exam_attempt WHERE id=?",String.class,attempt));
  execute(user,new Command("SUBMIT",null,attempt,null,Map.of(question,"answer"),null,null,null,null,null));
  assertThrows(BadRequestException.class,()->execute(teacher,new Command("GRADE",null,attempt,null,null,question,8,0L,"",3L)));
  assertNull(jdbc.queryForObject("SELECT score FROM exam_answer WHERE attempt_id=?",Integer.class,attempt));
 }
}
