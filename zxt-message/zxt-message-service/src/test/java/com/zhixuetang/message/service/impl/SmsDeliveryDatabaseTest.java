package com.zhixuetang.message.service.impl;

import com.zhixuetang.api.dto.sms.SmsInfoDTO;
import com.zhixuetang.message.domain.po.*;
import com.zhixuetang.message.service.*;
import com.zhixuetang.message.thirdparty.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real ledger transactions and leases; only the external SMS provider is simulated. */
@EnabledIfEnvironmentVariable(named = "ACCEPTANCE_DB_PASSWORD", matches = ".+")
class SmsDeliveryDatabaseTest {
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private SmsDeliveryTasks deliveries;
    private RecordingProvider provider;
    private INoticeTemplateService notices;
    private String task;

    @BeforeEach
    void setup() throws Exception {
        String base =
                "jdbc:mysql://127.0.0.1:"
                        + System.getenv().getOrDefault("ACCEPTANCE_DB_PORT", "23316")
                        + "/";
        String options = "?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true";
        new JdbcTemplate(
                        new DriverManagerDataSource(
                                base + options, "root", System.getenv("ACCEPTANCE_DB_PASSWORD")))
                .execute("CREATE DATABASE IF NOT EXISTS acceptance_sms_receipts");
        var source =
                new DriverManagerDataSource(
                        base + "acceptance_sms_receipts" + options,
                        "root",
                        System.getenv("ACCEPTANCE_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        assertEquals(
                "acceptance_sms_receipts", jdbc.queryForObject("SELECT DATABASE()", String.class));
        Path migrations =
                Path.of("../../deploy/acceptance/migrations/tj_message")
                        .toAbsolutePath()
                        .normalize();
        // This fixture needs the SMS tables only; V002 also alters unrelated inbox tables.
        for (String statement :
                Files.readString(migrations.resolve("V002__durable_delivery.sql")).split(";")) {
            if (statement.strip().startsWith("CREATE TABLE"))
                jdbc.execute(statement.replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS "));
        }
        if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sms_delivery_task' AND column_name='version'",
                        Integer.class)
                == 0)
            jdbc.execute(
                    Files.readString(migrations.resolve("V004__delivery_versions.sql"))
                            .replace(";", ""));
        if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='sms_delivery_recipient'",
                        Integer.class)
                == 0) {
            String sql =
                    Files.readString(migrations.resolve("V005__provider_sms_receipts.sql"))
                            .replaceAll("(?m)^--.*$", "");
            for (String statement : sql.split(";"))
                if (!statement.isBlank()) jdbc.execute(statement);
        }
        manager = new DataSourceTransactionManager(source);
        provider = new RecordingProvider();
        var platforms = mock(ISmsThirdPlatformService.class);
        notices = mock(INoticeTemplateService.class);
        var templates = mock(IMessageTemplateService.class);
        var notice = new NoticeTemplate();
        notice.setId(1L);
        notice.setStatus(1);
        notice.setIsSmsTemplate(true);
        when(notices.queryByCode("VERIFY_CODE")).thenReturn(notice);
        when(platforms.queryAllPlatform())
                .thenReturn(List.of(new SmsThirdPlatform().setCode("aliYun").setStatus(1)));
        when(templates.queryByNoticeTemplateId(1L))
                .thenReturn(
                        List.of(
                                new MessageTemplate()
                                        .setPlatformCode("aliYun")
                                        .setStatus(1)
                                        .setSignName("Fixture")
                                        .setThirdTemplateCode("SMS_FIXTURE")));
        var registry =
                new SmsProviderRegistry(Map.of("aliYun", provider), platforms, notices, templates);
        var beans = new StaticListableBeanFactory(Map.of("smsProviderRegistry", registry));
        deliveries =
                new SmsDeliveryTasks(
                        jdbc,
                        JsonMapper.builder().build(),
                        beans.getBeanProvider(SmsProviderRegistry.class),
                        new ThreadPoolTaskExecutor(),
                        manager,
                        new MockEnvironment());
        task = UUID.randomUUID().toString();
    }

    @AfterEach
    void cleanup() {
        if (jdbc != null && task != null) {
            jdbc.update("DELETE FROM sms_delivery_recipient WHERE task_id=?", task);
            jdbc.update("DELETE FROM sms_delivery_task WHERE id=?", task);
            jdbc.update("DELETE FROM simulated_sms WHERE task_id=?", task);
        }
    }

    @Test
    void acceptanceIsNotDeliveryAndIndividualRecipientsSettleIndependently() {
        deliveries.stage(
                task, message(System.currentTimeMillis() + 300_000, "13800000001", "13800000002"));
        var ids = recipients();
        deliveries.process(ids.get(0));
        deliveries.process(ids.get(1));
        assertEquals("ACCEPTED", parent());
        assertEquals(2, provider.sends.get());
        provider.receipt = new ISmsHandler.Receipt(ISmsHandler.State.DELIVERED, "DELIVERED");
        due(ids.get(0));
        deliveries.process(ids.get(0));
        assertEquals("ACCEPTED", parent());
        provider.receipt = new ISmsHandler.Receipt(ISmsHandler.State.FAILED, "isv.REJECTED");
        due(ids.get(1));
        deliveries.process(ids.get(1));
        assertEquals("DEAD", parent());
        assertEquals(
                List.of("DELIVERED", "FAILED"),
                jdbc.queryForList(
                        "SELECT state FROM sms_delivery_recipient WHERE task_id=? ORDER BY phone",
                        String.class,
                        task));
    }

    @Test
    void ambiguousSendOnlyQueriesAndNeverResendsEvenWithoutMatchingReceipt() {
        deliveries.stage(task, message(System.currentTimeMillis() + 300_000, "13800000001"));
        String id = recipients().getFirst();
        provider.failure = new SmsProviderFailure("SMS_REQUEST_RESULT_UNKNOWN", true);
        deliveries.process(id);
        assertEquals("UNKNOWN", parent());
        String correlation =
                jdbc.queryForObject(
                        "SELECT out_id FROM sms_delivery_recipient WHERE id=?", String.class, id);
        provider.failure = null;
        provider.receipt =
                new ISmsHandler.Receipt(ISmsHandler.State.UNKNOWN, "SMS_RECEIPT_NOT_FOUND");
        due(id);
        deliveries.process(id);
        assertEquals("UNKNOWN", parent());
        assertEquals(1, provider.sends.get());
        provider.receipt = new ISmsHandler.Receipt(ISmsHandler.State.DELIVERED, "DELIVERED");
        due(id);
        deliveries.process(id);
        assertEquals("DONE", parent());
        assertEquals(correlation, provider.queriedOutId);
        assertEquals(1, provider.sends.get());
    }

    @Test
    void restartAfterIntentCommitReconcilesWithoutSendingAgain() {
        deliveries.stage(task, message(System.currentTimeMillis() + 300_000, "13800000001"));
        String id = recipients().getFirst();
        jdbc.update(
                "UPDATE sms_delivery_recipient SET state='SUBMITTING',provider_code='aliYun',provider_template='SMS_FIXTURE',out_id='persisted-out-id',submitted_at=NOW(3),receipt_deadline=NOW(3)+INTERVAL 1 DAY,lease_until=NOW(3)-INTERVAL 1 MINUTE WHERE id=?",
                id);
        provider.receipt = new ISmsHandler.Receipt(ISmsHandler.State.DELIVERED, "DELIVERED");
        deliveries.process(id);
        assertEquals("DONE", parent());
        assertEquals(0, provider.sends.get());
        assertEquals("persisted-out-id", provider.queriedOutId);
    }

    @Test
    void expiredVerificationIsNeverSubmittedAndExpiryCannotBeOmitted() {
        assertThrows(
                com.zhixuetang.common.exceptions.BadRequestException.class,
                () -> deliveries.stage(task, message(null, "13800000001")));
        deliveries.stage(task, message(System.currentTimeMillis() - 1_000, "13800000001"));
        deliveries.process(recipients().getFirst());
        assertEquals("EXPIRED", parent());
        assertEquals(0, provider.sends.get());
    }

    @Test
    void verificationExpiringDuringRouteSelectionCannotCrossTheSubmissionBoundary() {
        deliveries.stage(task, message(System.currentTimeMillis() + 300_000, "13800000001"));
        String id = recipients().getFirst();
        when(notices.queryByCode("VERIFY_CODE"))
                .thenAnswer(
                        invocation -> {
                            jdbc.update(
                                    "UPDATE sms_delivery_recipient SET expires_at=NOW(3)-INTERVAL 1 SECOND WHERE id=?",
                                    id);
                            return new NoticeTemplate()
                                    .setId(1L)
                                    .setStatus(1)
                                    .setIsSmsTemplate(true);
                        });
        deliveries.process(id);
        assertEquals(0, provider.sends.get());
        assertEquals(
                "PENDING",
                jdbc.queryForObject(
                        "SELECT state FROM sms_delivery_recipient WHERE id=?", String.class, id));
    }

    @Test
    void explicitProviderRejectionCanRetryButUsesANewCorrelationId() {
        deliveries.stage(task, message(System.currentTimeMillis() + 300_000, "13800000001"));
        String id = recipients().getFirst();
        provider.failure = new SmsProviderFailure("isv.BUSINESS_LIMIT_CONTROL", false);
        deliveries.process(id);
        assertEquals("PENDING", parent());
        String previous =
                jdbc.queryForObject(
                        "SELECT out_id FROM sms_delivery_recipient WHERE id=?", String.class, id);
        provider.failure = null;
        due(id);
        deliveries.process(id);
        assertEquals("ACCEPTED", parent());
        assertNotEquals(
                previous,
                jdbc.queryForObject(
                        "SELECT out_id FROM sms_delivery_recipient WHERE id=?", String.class, id));
    }

    @Test
    void stageSharesCallerTransactionAndDuplicateEventsDoNotDuplicateRecipients() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        new TransactionTemplate(manager)
                                .executeWithoutResult(
                                        status -> {
                                            deliveries.stage(
                                                    task,
                                                    message(
                                                            System.currentTimeMillis() + 300_000,
                                                            "13800000001"));
                                            throw new IllegalStateException("rollback");
                                        }));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sms_delivery_task WHERE id=?", Integer.class, task));
        var form = message(System.currentTimeMillis() + 300_000, "13800000001", "13800000001");
        deliveries.stage(task, form);
        deliveries.stage(task, form);
        assertEquals(1, recipients().size());
    }

    @Test
    void concurrentWorkerCannotStealAnActiveSubmissionLease() throws Exception {
        deliveries.stage(task, message(System.currentTimeMillis() + 300_000, "13800000001"));
        String id = recipients().getFirst();
        provider.entered = new CountDownLatch(1);
        provider.release = new CountDownLatch(1);
        var first = CompletableFuture.runAsync(() -> deliveries.process(id));
        try {
            assertTrue(provider.entered.await(3, TimeUnit.SECONDS));
            deliveries.process(id);
            assertEquals(1, provider.sends.get());
        } finally {
            provider.release.countDown();
        }
        first.get(3, TimeUnit.SECONDS);
        assertEquals("ACCEPTED", parent());
    }

    @Test
    void operatorCannotReplayUnknownResultsAndReceiptListingDoesNotExposePhoneOrCode() {
        deliveries.stage(task, message(System.currentTimeMillis() + 300_000, "13800000001"));
        provider.failure = new SmsProviderFailure("SMS_REQUEST_RESULT_UNKNOWN", true);
        deliveries.process(recipients().getFirst());
        var controller = new com.zhixuetang.message.controller.DeliveryTaskController(jdbc);
        com.zhixuetang.common.utils.UserContext.setUser(1L);
        com.zhixuetang.common.utils.UserContext.setRole(1L);
        try {
            long version =
                    jdbc.queryForObject(
                            "SELECT version FROM sms_delivery_task WHERE id=?", Long.class, task);
            assertThrows(
                    com.zhixuetang.common.exceptions.ConflictException.class,
                    () ->
                            new TransactionTemplate(manager)
                                    .executeWithoutResult(
                                            status -> controller.replay("sms", task, version)));
            String response =
                    JsonMapper.builder().build().writeValueAsString(controller.list("sms", task));
            assertTrue(response.contains("***0001"));
            assertFalse(response.contains("13800000001"));
            assertFalse(response.contains("012345"));
        } finally {
            com.zhixuetang.common.utils.UserContext.removeUser();
        }
    }

    @Test
    void simulatorIsRecordedExplicitlyAndDoesNotCountAsProviderDelivery() {
        var environment = new MockEnvironment().withProperty("zxt.sms.simulated", "true");
        environment.setActiveProfiles("acceptance");
        var beans = new StaticListableBeanFactory();
        var simulator =
                new SmsDeliveryTasks(
                        jdbc,
                        JsonMapper.builder().build(),
                        beans.getBeanProvider(SmsProviderRegistry.class),
                        new ThreadPoolTaskExecutor(),
                        manager,
                        environment);
        simulator.stage(task, message(System.currentTimeMillis() + 300_000, "13800000001"));
        simulator.process(recipients().getFirst());
        assertEquals("DONE", parent());
        assertEquals(
                "SIMULATED",
                jdbc.queryForObject(
                        "SELECT state FROM sms_delivery_recipient WHERE task_id=?",
                        String.class,
                        task));
        assertEquals(0, provider.sends.get());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM simulated_sms WHERE task_id=?", Integer.class, task));
    }

    private SmsInfoDTO message(Long expiry, String... phones) {
        var value = new SmsInfoDTO();
        value.setPhones(List.of(phones));
        value.setTemplateCode("VERIFY_CODE");
        value.setTemplateParams(Map.of("code", "012345"));
        value.setExpiresAt(expiry);
        return value;
    }

    private List<String> recipients() {
        return jdbc.queryForList(
                "SELECT id FROM sms_delivery_recipient WHERE task_id=? ORDER BY phone",
                String.class,
                task);
    }

    private String parent() {
        return jdbc.queryForObject(
                "SELECT status FROM sms_delivery_task WHERE id=?", String.class, task);
    }

    private void due(String id) {
        jdbc.update(
                "UPDATE sms_delivery_recipient SET next_attempt_at=NOW(3)-INTERVAL 1 SECOND WHERE id=?",
                id);
    }

    private static class RecordingProvider implements ISmsHandler {
        final AtomicInteger sends = new AtomicInteger();
        volatile SmsProviderFailure failure;
        volatile Receipt receipt = new Receipt(State.PENDING, null);
        volatile String queriedOutId;
        CountDownLatch entered, release;

        public Submission send(
                String phone,
                String template,
                String sign,
                Map<String, String> parameters,
                String outId) {
            sends.incrementAndGet();
            if (entered != null) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS))
                        throw new IllegalStateException("fixture timeout");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
            }
            if (failure != null) throw failure;
            return new Submission("fixture-biz", "fixture-request");
        }

        public Receipt query(
                String phone, LocalDate date, String biz, String outId, String template) {
            queriedOutId = outId;
            return receipt;
        }
    }
}
