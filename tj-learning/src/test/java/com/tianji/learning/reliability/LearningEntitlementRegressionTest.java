package com.tianji.learning.reliability;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.learning.service.impl.LearningEntitlementService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class LearningEntitlementRegressionTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private LearningEntitlementService service;
    private long user, course;
    private final LocalDateTime purchased = LocalDateTime.now().withNano(0);

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:" + System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316") + "/acceptance_learning?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true", "root", System.getenv("ACCEPTANCE_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        service = new LearningEntitlementService(jdbc);
        user = IdWorker.getId(); course = user + 1;
    }

    private OrderBasicDTO order(long detail) {
        var order = new OrderBasicDTO();
        order.setOrderId(detail + 1); order.setUserId(user); order.setCourseIds(List.of(course));
        order.setDetailIds(Map.of(course, detail)); order.setFinishTime(purchased);
        return order;
    }
    private void grant(long detail, Integer duration) {
        var info = new CourseSimpleInfoDTO(); info.setId(course); info.setValidDuration(duration);
        var event=order(detail);event.setValidDurations(Map.of(course,duration==null?0:duration));
        transactions.executeWithoutResult(status -> service.grant(event, List.of(info)));
    }
    private void revoke(long detail) { transactions.executeWithoutResult(status -> service.revoke(order(detail))); }
    private LocalDateTime expiry() { return jdbc.queryForObject("SELECT expire_time FROM learning_lesson WHERE user_id=? AND course_id=?", LocalDateTime.class, user, course); }

    @Test void refundOfLongerEntitlementShrinksLessonToRemainingExpiry() {
        grant(user + 10, 1); grant(user + 20, 2); revoke(user + 20);
        assertEquals(purchased.plusMonths(1), expiry());
    }
    @Test void refundOfPermanentEntitlementRestoresFiniteExpiry() {
        grant(user + 10, null); grant(user + 20, 1); revoke(user + 10);
        assertEquals(purchased.plusMonths(1), expiry());
    }
    @Test void revokeBeforeGrantCannotResurrectEntitlement() {
        revoke(user + 10); grant(user + 10, 1);
        assertEquals(0, jdbc.queryForObject("SELECT active FROM learning_entitlement WHERE order_detail_id=?", Integer.class, user + 10));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM learning_lesson WHERE user_id=? AND course_id=? AND status<>3", Integer.class, user, course));
    }
    @Test void allRevokedDisablesAccessAndRegrantPreservesCompletedHistory() {
        grant(user+10,1);
        jdbc.update("UPDATE learning_lesson SET status=2,learned_sections=4 WHERE user_id=? AND course_id=?",user,course);
        long lesson=jdbc.queryForObject("SELECT id FROM learning_lesson WHERE user_id=? AND course_id=?",Long.class,user,course);
        jdbc.update("INSERT INTO learning_record(id,lesson_id,section_id,user_id,moment,finished) VALUES(?,?,?,?,10,1)",user+50,lesson,user+51,user);
        revoke(user+10);
        assertNull(transactions.execute(status->service.available(user,course)));
        assertEquals(3,jdbc.queryForObject("SELECT status FROM learning_lesson WHERE id=?",Integer.class,lesson));
        grant(user+20,2);
        long restored=transactions.execute(status->service.require(user,course));assertEquals(lesson,restored);
        assertEquals(2,jdbc.queryForObject("SELECT status FROM learning_lesson WHERE id=?",Integer.class,lesson));
        assertEquals(4,jdbc.queryForObject("SELECT learned_sections FROM learning_lesson WHERE id=?",Integer.class,lesson));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_record WHERE lesson_id=?",Integer.class,lesson));
    }
    @Test void projectedPermanentLessonWithoutAnEntitlementCannotAuthorizeAccess() {
        jdbc.update("INSERT INTO learning_lesson(id,user_id,course_id,status,learned_sections) VALUES(?,?,?,0,0)",user+70,user,course);
        assertNull(transactions.execute(status->service.available(user,course)));
        assertTrue(service.summaries(user,List.of(course)).isEmpty());
    }
    @Test void displaySummaryShrinksExpiryAndNeverCallsRevokedRightsPermanent() {
        grant(user+10,null);grant(user+20,1);
        assertNull(service.summaries(user,List.of(course)).get(course).expiresAt());
        revoke(user+10);assertEquals(purchased.plusMonths(1),service.summaries(user,List.of(course)).get(course).expiresAt());
        revoke(user+20);assertTrue(service.summaries(user,List.of(course)).isEmpty());
    }
    private void legacyEvent(boolean revoked,long returnedUser) {
        long detail=user+10;var event=order(detail);
        var trade=org.mockito.Mockito.mock(com.tianji.api.client.trade.TradeClient.class);
        org.mockito.Mockito.when(trade.orderEntitlements(detail+1)).thenReturn(List.of(new com.tianji.api.dto.trade.OrderEntitlementDTO(detail+1,detail,returnedUser,course,1,purchased,revoked)));
        var inbox=new com.tianji.common.autoconfigure.reliability.InboxStore(jdbc,transactions);
        var listener=new com.tianji.learning.mq.LessonChangeListener(service,inbox,trade);
        var properties=new org.springframework.amqp.core.MessageProperties();properties.setMessageId(UUID.randomUUID().toString());
        listener.listenLessonPay(event,new org.springframework.amqp.core.Message(new byte[0],properties));
    }
    @Test void legacyGrantUsesRetainedPurchaseFactsWithoutCurrentCatalogueDependency() {
        legacyEvent(false,user);assertEquals(purchased.plusMonths(1),expiry());
    }
    @Test void legacyGrantWithRetainedRefundCannotReopenLearning() {
        legacyEvent(true,user);legacyEvent(true,user);
        assertNull(transactions.execute(status->service.available(user,course)));
        assertEquals(0,jdbc.queryForObject("SELECT active FROM learning_entitlement WHERE order_detail_id=?",Integer.class,user+10));
    }
    @Test void legacyFactsForAnotherUserAreRejectedBeforeWriting() {
        assertThrows(IllegalArgumentException.class,()->legacyEvent(false,user+1));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_entitlement WHERE user_id=?",Integer.class,user));
    }
    @Test void concurrentGrantRevokeAndReplayKeepTheRemainingExpiry() throws Exception {
        grant(user+10,1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(12)) {
            var work=new ArrayList<java.util.concurrent.Future<?>>();
            for(int index=0;index<30;index++) {
                long detail=user+100+index*2;
                work.add(pool.submit(()->{grant(detail,2);revoke(detail);grant(detail,2);revoke(detail);}));
            }
            for(var future:work) future.get(30,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(purchased.plusMonths(1),expiry());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_entitlement WHERE user_id=? AND course_id=? AND active=1",Integer.class,user,course));
        assertNull(transactions.execute(status->service.available(user+1,course)));
    }
    @Test void purchasedDurationWinsOverChangedCatalogueDuration() {
        var metadata=new CourseSimpleInfoDTO();metadata.setId(course);metadata.setValidDuration(99);
        var event=order(user+10);event.setValidDurations(Map.of(course,1));
        transactions.executeWithoutResult(status->service.grant(event,List.of(metadata)));
        assertEquals(purchased.plusMonths(1),expiry());
    }
    @Test void expiredEntitlementDoesNotAuthorizeAProjectedLesson() {
        var metadata=new CourseSimpleInfoDTO();metadata.setId(course);
        var event=order(user+10);event.setFinishTime(purchased.minusMonths(2));event.setValidDurations(Map.of(course,1));
        transactions.executeWithoutResult(status->service.grant(event,List.of(metadata)));
        assertNull(transactions.execute(status->service.available(user,course)));
        assertEquals(3,jdbc.queryForObject("SELECT status FROM learning_lesson WHERE user_id=? AND course_id=?",Integer.class,user,course));
    }
    @Test void revokeWaitsForTheProtectedWriteAndKeepsItsHistory()throws Exception {
        grant(user+10,1);var locked=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var learning=pool.submit(()->transactions.executeWithoutResult(status->{
                long lesson=service.require(user,course);locked.countDown();
                try{assertTrue(release.await(5,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException error){throw new RuntimeException(error);}
                jdbc.update("INSERT INTO learning_record(id,lesson_id,section_id,user_id,moment,finished) VALUES(?,?,?,?,10,0)",user+80,lesson,user+81,user);
            }));
            assertTrue(locked.await(5,java.util.concurrent.TimeUnit.SECONDS));var refund=pool.submit(()->revoke(user+10));
            try{assertThrows(java.util.concurrent.TimeoutException.class,()->refund.get(100,java.util.concurrent.TimeUnit.MILLISECONDS));}finally{release.countDown();}
            learning.get(5,java.util.concurrent.TimeUnit.SECONDS);refund.get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertNull(transactions.execute(status->service.available(user,course)));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_record WHERE id=?",Integer.class,user+80));
        }
    }
}
