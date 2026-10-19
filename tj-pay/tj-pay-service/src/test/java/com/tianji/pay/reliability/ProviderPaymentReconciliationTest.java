package com.tianji.pay.reliability;

import com.tianji.common.autoconfigure.reliability.OutboxStore;
import com.tianji.pay.service.impl.*;
import com.tianji.pay.controller.ProviderReconciliationController;
import com.tianji.common.utils.UserContext;
import com.tianji.pay.third.IPayService;
import com.tianji.pay.third.local.SimulatedPayService;
import com.tianji.pay.third.model.PayStatusResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class ProviderPaymentReconciliationTest {
    private static TimeZone originalTimeZone;
    @BeforeAll static void useProductionTimeZone() {
        originalTimeZone=TimeZone.getDefault();
        // Production JVM and the forced JDBC session both use Asia/Shanghai.
        // Pin the fixture's JVM too, even when CI itself starts in UTC.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        assertEquals("Asia/Shanghai",TimeZone.getDefault().getID());
        System.out.println("Payment reconciliation timezone fixture: original="+originalTimeZone.getID()+", active="+TimeZone.getDefault().getID());
    }
    @AfterAll static void restoreTimeZone() {
        if(originalTimeZone!=null) {
            TimeZone.setDefault(originalTimeZone);
            assertEquals(originalTimeZone.getID(),TimeZone.getDefault().getID());
            System.out.println("Payment reconciliation timezone fixture restored="+TimeZone.getDefault().getID());
        }
    }
    JdbcTemplate jdbc;DataSourceTransactionManager manager;ProviderSettlementService settlement;
    ProviderPaymentReconciler worker;IPayService provider;long number,business;
    final List<Long> inserted=new ArrayList<>();
    @BeforeEach void setup() {
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_pay?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
        jdbc=new JdbcTemplate(source);manager=new DataSourceTransactionManager(source);
        settlement=new ProviderSettlementService(jdbc,new OutboxStore(jdbc,JsonMapper.builder().build()));
        provider=mock(IPayService.class);worker=worker(provider);
        number=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();business=number+1;insert(number,1);
    }
    ProviderPaymentReconciler worker(IPayService channel) {
        var inline=new ThreadPoolTaskExecutor(){@Override public void execute(Runnable task){task.run();}};
        return new ProviderPaymentReconciler(jdbc,settlement,Map.of("mockPay",channel),inline,manager);
    }
    void insert(long id,int status) {
        inserted.add(id);
        jdbc.update("INSERT INTO pay_order(id,biz_order_no,pay_order_no,biz_user_id,pay_channel_code,amount,status,pay_over_time,create_time) VALUES(?,?,?,7,'mockPay',1000,?,NOW()-INTERVAL 1 HOUR,NOW()-INTERVAL 3 HOUR)",id,id+1,id,status);
        worker.ensure(id);
    }
    PayStatusResponse paid(long id) {return PayStatusResponse.builder().success(true).payOrderNo(Long.toString(id)).payStatus(3).totalAmount(1000).successTime(LocalDateTime.now().minusHours(2)).build();}
    int count(String table,long id) {return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE pay_order_no=?",Integer.class,id);}
    @AfterEach void cleanup() {
        UserContext.removeUser();
        for(long id:inserted) {
            jdbc.update("DELETE FROM reliability_outbox WHERE business_key=?","provider:"+id+":paid");
            for(String table:List.of("provider_payment_anomaly","provider_payment_reconcile","provider_payment_fact","simulated_provider_payment","pay_order"))jdbc.update("DELETE FROM "+table+" WHERE pay_order_no=?",id);
        }
    }
    @Test void expiredAndClosedOrdersStillQueryAndCommitOneLateFact() {
        jdbc.update("UPDATE pay_order SET status=2 WHERE pay_order_no=?",number);
        when(provider.queryPayOrderStatus(Long.toString(number))).thenReturn(paid(number));
        worker.reconcile(number);worker.reconcile(number);
        verify(provider,times(1)).queryPayOrderStatus(Long.toString(number));
        assertEquals(2,jdbc.queryForObject("SELECT status FROM pay_order WHERE pay_order_no=?",Integer.class,number));
        assertEquals(1,count("provider_payment_fact",number));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM reliability_outbox WHERE business_key=?",Integer.class,"provider:"+number+":paid"));
        assertEquals("DONE",jdbc.queryForObject("SELECT status FROM provider_payment_reconcile WHERE pay_order_no=?",String.class,number));
    }
    @Test void expiredOpenOrderIsCheckedBeforeLocalClosure() {
        when(provider.queryPayOrderStatus(Long.toString(number))).thenReturn(paid(number));
        worker.reconcile(number);
        assertEquals(3,jdbc.queryForObject("SELECT status FROM pay_order WHERE pay_order_no=?",Integer.class,number));
        assertEquals(1,count("provider_payment_fact",number));
    }
    @Test void mismatchedNumberAmountAndMissingTimestampArePersistedWithoutSettlement() {
        for(String error:List.of("ORDER_NUMBER_MISMATCH","AMOUNT_MISMATCH","INVALID_SUCCESS_TIME")) {
            PayStatusResponse response=paid(number);
            if(error.equals("ORDER_NUMBER_MISMATCH"))response.setPayOrderNo("other");
            if(error.equals("AMOUNT_MISMATCH"))response.setTotalAmount(999);
            if(error.equals("INVALID_SUCCESS_TIME"))response.setSuccessTime(null);
            when(provider.queryPayOrderStatus(Long.toString(number))).thenReturn(response);
            jdbc.update("UPDATE provider_payment_reconcile SET next_attempt_at=NOW(3) WHERE pay_order_no=?",number);
            worker.reconcile(number);
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM provider_payment_anomaly WHERE pay_order_no=? AND reason=?",Integer.class,number,error));
            assertEquals(0,count("provider_payment_fact",number));
            assertEquals(1,jdbc.queryForObject("SELECT status FROM pay_order WHERE pay_order_no=?",Integer.class,number));
        }
    }
    @Test void transientQueryOutageKeepsRetryingPastTenAttemptsUntilWindowEnds() {
        jdbc.update("UPDATE provider_payment_reconcile SET attempts=12 WHERE pay_order_no=?",number);
        when(provider.queryPayOrderStatus(Long.toString(number))).thenThrow(new IllegalStateException("provider unavailable"));
        worker.reconcile(number);
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM provider_payment_reconcile WHERE pay_order_no=?",String.class,number));
        assertTrue(jdbc.queryForObject("SELECT next_attempt_at>CURRENT_TIMESTAMP(3) FROM provider_payment_reconcile WHERE pay_order_no=?",Boolean.class,number));
        jdbc.update("UPDATE provider_payment_reconcile SET expires_at=NOW()-INTERVAL 1 SECOND,next_attempt_at=NOW(3) WHERE pay_order_no=?",number);
        worker.reconcile(number);
        assertEquals("DEAD",jdbc.queryForObject("SELECT status FROM provider_payment_reconcile WHERE pay_order_no=?",String.class,number));
        assertNotNull(jdbc.queryForObject("SELECT last_error FROM provider_payment_reconcile WHERE pay_order_no=?",String.class,number));
    }
    @Test void pollDoesNotStarveOrdersBeyondTheFirstHundred() {
        when(provider.queryPayOrderStatus(anyString())).thenAnswer(call->paid(Long.parseLong(call.getArgument(0))));
        for(int i=1;i<121;i++)insert(number+i*10,2);
        worker.poll();worker.poll();
        for(long id:inserted)assertEquals(1,count("provider_payment_fact",id));
    }
    @Test void independentlyCommittedSimulatorLedgerIsRecoveredAfterNotificationLoss() {
        jdbc.update("INSERT INTO simulated_provider_payment(pay_order_no,amount) VALUES(?,1000)",number);
        var env=new MockEnvironment().withProperty("tj.acceptance.simulated-payment.drop-notification-business",Long.toString(business));env.setActiveProfiles("acceptance");
        var ledger=new SimulatedProviderLedger(jdbc,manager,env);
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(manager).executeWithoutResult(tx->{
            assertTrue(ledger.confirm(business,7).notificationDropped());throw new IllegalStateException("business notification lost");
        }));
        assertEquals(3,jdbc.queryForObject("SELECT status FROM simulated_provider_payment WHERE pay_order_no=?",Integer.class,number));
        assertEquals(0,count("provider_payment_fact",number));
        worker(new SimulatedPayService(jdbc,env)).reconcile(number);
        assertEquals(1,count("provider_payment_fact",number));
    }
    @Test void anomaliesHaveProtectedVersionedResolutionAndScrapesUseCachedMetrics() {
        var response=paid(number);response.setTotalAmount(999);
        when(provider.queryPayOrderStatus(Long.toString(number))).thenReturn(response);worker.reconcile(number);
        var admin=new ProviderReconciliationController(jdbc);
        UserContext.setUser(7L);UserContext.setRole(2L);
        assertThrows(com.tianji.common.exceptions.ForbiddenException.class,()->admin.anomalies("OPEN"));
        UserContext.setRole(1L);
        long version=jdbc.queryForObject("SELECT version FROM provider_payment_anomaly WHERE pay_order_no=? AND reason='AMOUNT_MISMATCH'",Long.class,number);
        var resolution=new ProviderReconciliationController.Resolution(version,"已独立核对渠道账单，保留原始事实");
        admin.resolve(number,"AMOUNT_MISMATCH",resolution);
        assertThrows(com.tianji.common.exceptions.ConflictException.class,()->admin.resolve(number,"AMOUNT_MISMATCH",resolution));
        assertEquals("RESOLVED",jdbc.queryForObject("SELECT status FROM provider_payment_anomaly WHERE pay_order_no=?",String.class,number));
        var registry=new io.micrometer.core.instrument.simple.SimpleMeterRegistry();var watched=spy(jdbc);var metrics=new ProviderReconciliationMetrics(watched,registry);
        metrics.refresh();double expected=jdbc.queryForObject("SELECT COUNT(*) FROM provider_payment_anomaly WHERE status='OPEN'",Double.class);
        clearInvocations(watched);
        assertEquals(expected,registry.get("tj.payment.anomalies").gauge().value());registry.get("tj.payment.reconciliation.dead").gauge().value();verifyNoInteractions(watched);
        jdbc.update("UPDATE provider_payment_reconcile SET next_attempt_at=NOW(3) WHERE pay_order_no=?",number);worker.reconcile(number);
        assertEquals("OPEN",jdbc.queryForObject("SELECT status FROM provider_payment_anomaly WHERE pay_order_no=?",String.class,number));
        jdbc.update("UPDATE provider_payment_reconcile SET status='DEAD' WHERE pay_order_no=?",number);
        long taskVersion=jdbc.queryForObject("SELECT version FROM provider_payment_reconcile WHERE pay_order_no=?",Long.class,number);
        admin.replay(number,new ProviderReconciliationController.Resolution(taskVersion,"已处理配置差异，重新核对"));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM provider_payment_reconcile WHERE pay_order_no=?",String.class,number));
        assertEquals(7L,jdbc.queryForObject("SELECT replayed_by FROM provider_payment_reconcile WHERE pay_order_no=?",Long.class,number));
        registry.close();
    }
}
