package com.tianji.exam.reliability;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.common.autoconfigure.mvc.advice.CommonExceptionAdvice;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.controller.InternalExamController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL and MVC: counts include every paper and never expose answers or another actor's attempts. */
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class LearningExamReminderTest {
    private JdbcTemplate jdbc;private MockMvc mvc;private long user,other,course;
    @BeforeEach void prepare() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_exam?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true","root",System.getenv("ACCEPTANCE_DB_PASSWORD")));
        assertEquals("acceptance_exam",jdbc.queryForObject("SELECT DATABASE()",String.class));
        user=IdWorker.getId();other=IdWorker.getId();course=IdWorker.getId();UserContext.setUser(user);
        mvc=MockMvcBuilders.standaloneSetup(new InternalExamController(jdbc)).setControllerAdvice(new CommonExceptionAdvice()).build();
        assertNotNull(System.getenv("TJ_INTERNAL_TOKEN"));assertTrue(System.getenv("TJ_INTERNAL_TOKEN").length()>=32);
    }
    @AfterEach void cleanup(){UserContext.removeUser();}
    private long paper(long section,int version){long id=IdWorker.getId();jdbc.update("INSERT INTO exam_paper(id,course_id,section_id,version,total_score,pass_percent,section_count) VALUES(?,?,?,?,10,60,1)",id,course,section,version);return id;}
    private void attempt(long paper,long actor,String status,boolean passed){jdbc.update("INSERT INTO exam_attempt(id,paper_id,user_id,lesson_id,status,passed) VALUES(?,?,?,?,?,?)",IdWorker.getId(),paper,actor,course,status,passed);}
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String body){return post("/internal/v1/exams/learning-overview").header("X-Internal-Token",System.getenv("TJ_INTERNAL_TOKEN")).contentType("application/json").content(body);}
    @Test void latestPaperCountsAreOwnerScopedAndSeparateWaitingFromActionable()throws Exception {
        long section=IdWorker.getId(),old=paper(section,1),latest=paper(section,2);attempt(old,user,"FINISHED",true);attempt(latest,other,"FINISHED",true);
        long waiting=paper(IdWorker.getId(),1);attempt(waiting,user,"WAIT_GRADING",false);attempt(waiting,user,"FINISHED",false);
        long passed=paper(IdWorker.getId(),1);attempt(passed,user,"FINISHED",true);attempt(passed,user,"FINISHED",false);
        long active=paper(IdWorker.getId(),1);attempt(active,user,"IN_PROGRESS",false);
        long failed=paper(IdWorker.getId(),1);attempt(failed,user,"FINISHED",false);attempt(failed,user,"FINISHED",false);
        paper(IdWorker.getId(),1);
        mvc.perform(request("["+course+"]").param("userId",Long.toString(other))).andExpect(status().isOk()).andExpect(jsonPath("$.pendingExams").value(4)).andExpect(jsonPath("$.waitingForGradingExams").value(1)).andExpect(jsonPath("$.answer").doesNotExist());
    }
    @Test void moreThanOneHundredPapersAreCountedWithoutPagination()throws Exception {
        for(int i=0;i<121;i++)paper(IdWorker.getId(),1);
        mvc.perform(request("["+course+"]")).andExpect(status().isOk()).andExpect(jsonPath("$.pendingExams").value(121)).andExpect(jsonPath("$.waitingForGradingExams").value(0));
    }
    @Test void internalTokenAndActorAreRequiredAndRpcLimitIsNotRelaxed()throws Exception {
        mvc.perform(post("/internal/v1/exams/learning-overview").contentType("application/json").content("["+course+"]")).andExpect(status().isForbidden());
        UserContext.removeUser();mvc.perform(request("["+course+"]")).andExpect(status().isUnauthorized());UserContext.setUser(user);
        for(String invalid:List.of("[]","[0]","[null]","[1,1]",java.util.stream.LongStream.rangeClosed(1,101).boxed().toList().toString()))mvc.perform(request(invalid)).andExpect(status().isBadRequest());
    }
}
