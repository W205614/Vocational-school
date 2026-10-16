package com.tianji.learning.reliability;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.api.client.exam.ExamClient;
import com.tianji.api.client.message.LearningNotificationClient;
import com.tianji.common.exceptions.UnauthorizedException;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.service.impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Entitlements and lessons are real MySQL; bounded downstream contracts are explicit doubles. */
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class LearningReminderTest {
    private JdbcTemplate jdbc;private long user,other;private ExamClient exams;private LearningNotificationClient notifications;private LearningReminderService service;
    @BeforeEach void prepare(){
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_learning?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true","root",System.getenv("ACCEPTANCE_DB_PASSWORD")));
        assertEquals("acceptance_learning",jdbc.queryForObject("SELECT DATABASE()",String.class));user=IdWorker.getId();other=IdWorker.getId();UserContext.setUser(user);
        exams=mock(ExamClient.class);notifications=mock(LearningNotificationClient.class);service=new LearningReminderService(jdbc,new LearningEntitlementService(jdbc),exams,notifications);
        when(notifications.unreadInteractions()).thenReturn(121);
    }
    @AfterEach void cleanup(){UserContext.removeUser();}
    private long course(long actor,int active,String expiry,int lessonStatus){long course=IdWorker.getId();jdbc.update("INSERT INTO learning_lesson(id,user_id,course_id,status,expire_time) VALUES(?,?,?,?,NULL)",IdWorker.getId(),actor,course,lessonStatus);jdbc.update("INSERT INTO learning_entitlement(order_detail_id,order_id,user_id,course_id,active,expires_at) VALUES(?,?,?,?,?,"+expiry+")",IdWorker.getId(),IdWorker.getId(),actor,course,active);return course;}
    @Test void activeEntitlementsAcrossAllCoursesAreBatchedAndStaleLessonStatusDoesNotGrantRights(){
        var expected=new HashSet<Long>();for(int i=0;i<121;i++)expected.add(course(user,1,"NULL",i%2==0?0:3));
        course(user,0,"NULL",0);course(user,1,"DATE_SUB(NOW(3),INTERVAL 1 DAY)",0);course(other,1,"NULL",0);
        var observed=new HashSet<Long>();var batches=new ArrayList<Integer>();
        when(exams.learningOverview(anyList())).thenAnswer(call->{List<Long> ids=call.getArgument(0);assertTrue(ids.size()<=100);assertTrue(expected.containsAll(ids));assertTrue(Collections.disjoint(observed,ids));observed.addAll(ids);batches.add(ids.size());return Map.of("pendingExams",ids.size()*2,"waitingForGradingExams",ids.size());});
        var result=service.overview();assertEquals(expected,observed);assertEquals(2,batches.size());assertEquals(242,result.get("pendingExams"));assertEquals(121,result.get("waitingForGradingExams"));assertEquals(121,result.get("unreadReplies"));assertNull(result.get("examReminderError"));assertNull(result.get("replyReminderError"));
    }
    @Test void unavailableReminderIsUnknownAndDoesNotEraseIndependentSuccessfulCount(){
        course(user,1,"NULL",0);when(exams.learningOverview(anyList())).thenThrow(new IllegalStateException("unavailable"));
        var failedExam=service.overview();assertNull(failedExam.get("pendingExams"));assertNull(failedExam.get("waitingForGradingExams"));assertNotNull(failedExam.get("examReminderError"));assertEquals(121,failedExam.get("unreadReplies"));
        doReturn(Map.of("pendingExams",2,"waitingForGradingExams",1)).when(exams).learningOverview(anyList());when(notifications.unreadInteractions()).thenThrow(new IllegalStateException("unavailable"));
        var failedMessages=service.overview();assertEquals(2,failedMessages.get("pendingExams"));assertNull(failedMessages.get("unreadReplies"));assertNotNull(failedMessages.get("replyReminderError"));assertNull(failedMessages.get("examReminderError"));
    }
    @Test void emptyRightsAreZeroButMalformedDownstreamOrAnonymousActorAreNot(){
        assertEquals(0,service.overview().get("pendingExams"));verifyNoInteractions(exams);
        course(user,1,"NULL",0);when(exams.learningOverview(anyList())).thenReturn(Map.of("pendingExams",2));when(notifications.unreadInteractions()).thenReturn(-1);
        var result=service.overview();assertNull(result.get("pendingExams"));assertNull(result.get("unreadReplies"));assertNotNull(result.get("examReminderError"));assertNotNull(result.get("replyReminderError"));
        UserContext.removeUser();assertThrows(UnauthorizedException.class,()->service.overview());
    }
}
