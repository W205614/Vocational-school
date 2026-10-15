package com.tianji.exam.reliability;

import com.tianji.common.autoconfigure.reliability.*;
import com.tianji.common.exceptions.*;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.controller.ExamWorkflowController;
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
class ExamReviewReliabilityTest {
    JdbcTemplate jdbc;TransactionTemplate tx;JsonMapper json;ExamReviewService reviews;ExamWorkflowService workflow;
    long paper,user,question,attempt,teacher;
    @BeforeEach void setup(){
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_exam?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
        jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));json=JsonMapper.builder().build();
        reviews=new ExamReviewService(jdbc,json);workflow=new ExamWorkflowService(jdbc,json,new OutboxStore(jdbc,json),org.mockito.Mockito.mock(com.tianji.api.client.learning.LearningClient.class));
        paper=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();user=paper+1;question=paper+2;attempt=paper+3;teacher=paper+4;
        jdbc.update("INSERT INTO exam_paper(id,course_id,section_id,version,total_score,pass_percent,section_count) VALUES(?,?,?,1,10,60,1)",paper,paper,paper);
        jdbc.update("INSERT INTO exam_paper_question(paper_id,question_id,position,name,type,score,options,answer,analysis) VALUES(?,?,1,'snapshot question',2,10,'[\"A\",\"B\"]','1,2','snapshot explanation')",paper,question);
        jdbc.update("INSERT INTO exam_attempt(id,paper_id,user_id,lesson_id,status,score,passed,graded_at) VALUES(?,?,?,?,'FINISHED',0,0,CURRENT_TIMESTAMP(3))",attempt,paper,user,paper);
        jdbc.update("INSERT INTO exam_answer(attempt_id,question_id,response,score,feedback) VALUES(?,?,'3',0,'revise this')",attempt,question);
    }
    Map<String,Object> execute(ExamReviewService.Command cmd){return tx.execute(s->(Map<String,Object>)reviews.execute(UUID.randomUUID().toString(),user,json.writeValueAsString(cmd)));}
    List<Map<String,Object>> list(Map<String,Object> page){return (List<Map<String,Object>>)page.get("list");}
    @Test void referenceAnswersAreSealedUntilWholePaperFinishedAndOwnerScoped(){
        for(String status:List.of("IN_PROGRESS","WAIT_GRADING")){
            jdbc.update("UPDATE exam_attempt SET status=? WHERE id=?",status,attempt);
            String value=json.writeValueAsString(workflow.view(attempt,user,false));assertFalse(value.contains("snapshot explanation"));
            assertFalse(((Map<?,?>)((List<?>)workflow.view(attempt,user,false).get("questions")).getFirst()).containsKey("answer"));
            assertEquals(0L,reviews.page(user,1,20,paper,null,null).get("total"));
            assertThrows(BadRequestException.class,()->reviews.detail(user,attempt,question));
        }
        jdbc.update("UPDATE exam_attempt SET status='FINISHED' WHERE id=?",attempt);
        var q=(Map<?,?>)((List<?>)workflow.view(attempt,user,false).get("questions")).getFirst();assertEquals("1,2",q.get("answer"));assertEquals("snapshot explanation",q.get("analysis"));
        assertThrows(BadRequestException.class,()->workflow.view(attempt,user+99,false));assertThrows(BadRequestException.class,()->reviews.detail(user+99,attempt,question));
        assertFalse(json.writeValueAsString(workflow.view(attempt,teacher,true)).contains("snapshot explanation"));
    }
    @Test void reviewIsPersistentVersionedAndNeverChangesOriginalGrade(){
        assertEquals(1L,reviews.page(user,1,20,paper,2,false).get("total"));assertEquals(0L,reviews.page(user,1,20,paper,5,false).get("total"));
        var result=execute(new ExamReviewService.Command("REVIEW",attempt,question,"2,1",null,0L));
        var history=(List<Map<String,Object>>)result.get("reviews");assertEquals(1,history.size());assertEquals(1,((Number)history.getFirst().get("correct")).intValue());assertEquals(1L,((Number)result.get("version")).longValue());
        execute(new ExamReviewService.Command("STATE",attempt,question,null,true,1L));
        var otherDevice=new ExamReviewService(jdbc,json);assertEquals(1,((Number)otherDevice.detail(user,attempt,question).get("mastered")).intValue());
        assertEquals(0L,otherDevice.page(user,1,20,paper,2,false).get("total"));assertEquals(1L,otherDevice.page(user,1,20,paper,2,true).get("total"));
        assertThrows(ConflictException.class,()->execute(new ExamReviewService.Command("STATE",attempt,question,null,false,1L)));
        execute(new ExamReviewService.Command("STATE",attempt,question,null,false,2L));
        assertEquals(1L,reviews.page(user,1,20,paper,2,false).get("total"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM exam_review_record WHERE attempt_id=?",Integer.class,attempt));
        assertEquals(0,jdbc.queryForObject("SELECT score FROM exam_attempt WHERE id=?",Integer.class,attempt));assertEquals("3",jdbc.queryForObject("SELECT response FROM exam_answer WHERE attempt_id=?",String.class,attempt));assertEquals(0,jdbc.queryForObject("SELECT score FROM exam_answer WHERE attempt_id=?",Integer.class,attempt));
    }
    @Test void concurrentDevicesCannotSilentlyOverwriteReviewState()throws Exception{
        AtomicInteger successes=new AtomicInteger(),conflicts=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(10)){
            List<Future<?>> tasks=new ArrayList<>();for(int i=0;i<20;i++)tasks.add(pool.submit(()->{try{execute(new ExamReviewService.Command("REVIEW",attempt,question,"1,2",null,0L));successes.incrementAndGet();}catch(ConflictException expected){conflicts.incrementAndGet();}}));
            for(var task:tasks)task.get(30,TimeUnit.SECONDS);
        }
        assertEquals(1,successes.get());assertEquals(19,conflicts.get());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM exam_review_record WHERE attempt_id=?",Integer.class,attempt));
    }
    @Test void durableOperationReplayReturnsOneReviewWithoutConsumingAnotherVersion(){
        var operations=new OperationStore(jdbc,json,tx);var command=new ExamReviewService.Command("REVIEW",attempt,question,"1,2",null,0L);String key="review-"+attempt;
        var submitted=operations.submit(user,reviews.kind(),key,command);String token=UUID.randomUUID().toString();assertTrue(operations.claim(submitted.operationId(),token));
        var work=new OperationStore.Work(submitted.operationId(),user,reviews.kind(),json.writeValueAsString(command),0);
        operations.execute(work,token,reviews);operations.execute(work,token,reviews);
        var replay=operations.submit(user,reviews.kind(),key,command);assertEquals(submitted.operationId(),replay.operationId());assertEquals("SUCCEEDED",replay.status());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM exam_review_record WHERE attempt_id=?",Integer.class,attempt));assertEquals(1L,((Number)reviews.detail(user,attempt,question).get("version")).longValue());
        assertThrows(ConflictException.class,()->operations.submit(user,reviews.kind(),key,new ExamReviewService.Command("REVIEW",attempt,question,"3",null,0L)));
    }
    @Test void subjectiveReviewNeedsSelfAssessmentAndFullMarksDoNotEnterReview(){
        jdbc.update("UPDATE exam_paper_question SET type=5 WHERE paper_id=?",paper);
        execute(new ExamReviewService.Command("REVIEW",attempt,question,"revised essay",null,0L));
        assertNull(jdbc.queryForObject("SELECT correct FROM exam_review_record WHERE attempt_id=?",Integer.class,attempt));
        jdbc.update("UPDATE exam_answer SET score=10 WHERE attempt_id=?",attempt);
        assertEquals(0L,reviews.page(user,1,20,paper,null,null).get("total"));assertThrows(BadRequestException.class,()->reviews.detail(user,attempt,question));
    }
    @Test void gradingStatisticsNeverGrantTaughtCourseOrOtherPaperAccess(){
        var controller=new ExamWorkflowController(workflow,null,null,null,jdbc,null);UserContext.setUser(teacher);UserContext.setRole(3L);
        try{
            assertEquals(0L,controller.statistics(1,20,paper,null).get("total"));
            jdbc.update("INSERT INTO exam_grader(paper_id,user_id) VALUES(?,?)",paper,teacher);
            var statistics=controller.statistics(1,20,paper,user);assertEquals("GRADING_ASSIGNMENT",statistics.get("scope"));assertEquals(1L,statistics.get("total"));assertEquals(0L,controller.statistics(1,20,paper,user+99).get("total"));
            UserContext.setRole(2L);assertThrows(ForbiddenException.class,()->controller.statistics(1,20,paper,null));
        }finally{UserContext.removeUser();}
    }
    @Test void invalidReviewRequestsCannotCreateState(){
        for(var cmd:List.of(new ExamReviewService.Command("REVIEW",attempt,question,"",null,0L),new ExamReviewService.Command("REVIEW",attempt,question,"x",null,null),new ExamReviewService.Command("STATE",attempt,question,null,null,0L)))assertThrows(BadRequestException.class,()->execute(cmd));
        assertThrows(BadRequestException.class,()->reviews.page(user,1,101,null,null,null));assertThrows(BadRequestException.class,()->reviews.page(user,1,20,null,6,null));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM exam_review_state WHERE attempt_id=?",Integer.class,attempt));
    }
}
