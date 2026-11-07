package com.tianji.user.privacy;

import com.tianji.api.client.privacy.PrivacyDomainClient;
import com.tianji.common.autoconfigure.privacy.PrivacyDomain;
import com.tianji.common.exceptions.*;
import com.tianji.common.utils.UserContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ACCEPTANCE_DB_PASSWORD", matches = ".+")
class PrivacyTaskDatabaseTest {
    @TempDir Path temporary;
    private JdbcTemplate jdbc;
    private PrivacyTaskService tasks;
    private PrivacyArchiveStore archives;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<String, Boundary> boundaries = new TreeMap<>();
    private Path policy;

    /** Only the remote-domain boundary is isolated; owning SQL and task/archive code are real. */
    private static final class Boundary implements PrivacyDomainClient {
        final String alias;
        final PrivacyDomain delegate;
        boolean unavailable;

        Boundary(String alias, PrivacyDomain delegate) {
            this.alias = alias;
            this.delegate = delegate;
        }

        public String domain() {
            return alias;
        }

        public PrivacyDomain.Definition inventory() {
            if (unavailable) throw new ServiceUnavailableException("synthetic remote unavailable");
            return delegate == null
                    ? new PrivacyDomain.Definition(
                            alias, "synthetic empty remote domain", List.of())
                    : delegate.inventory();
        }

        public PrivacyDomain.Page export(long user, String dataset, int offset) {
            if (unavailable) throw new ServiceUnavailableException("synthetic remote unavailable");
            return delegate.export(user, dataset, offset);
        }

        public Map<String, Object> plan(long user, Map<String, String> cutoffs) {
            if (unavailable) throw new ServiceUnavailableException("synthetic remote unavailable");
            return delegate == null
                    ? Map.of("aggregate", "NO_PERSONAL_DATA")
                    : delegate.plan(user, cutoffs);
        }

        public Map<String, Object> cleanup(PrivacyDomain.Cleanup request) {
            if (unavailable) throw new ServiceUnavailableException("synthetic remote unavailable");
            return delegate == null
                    ? Map.of("aggregate", "NO_PERSONAL_DATA")
                    : delegate.cleanup(request);
        }
    }

    @BeforeEach
    void setup() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("deploy/compact/schema.sql"))) root = root.getParent();
        String name = "acceptance_privacy_tasks_" + UUID.randomUUID().toString().substring(0, 8);
        String base =
                "jdbc:mysql://127.0.0.1:"
                        + System.getenv().getOrDefault("ACCEPTANCE_DB_PORT", "23316")
                        + "/";
        String options = "?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true";
        new JdbcTemplate(
                        new DriverManagerDataSource(
                                base + options, "root", System.getenv("ACCEPTANCE_DB_PASSWORD")))
                .execute("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4");
        var source =
                new DriverManagerDataSource(
                        base + name + options, "root", System.getenv("ACCEPTANCE_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        String schema = Files.readString(root.resolve("deploy/compact/schema.sql"));
        for (String table : List.of("user", "user_detail", "reliability_operation")) {
            var match =
                    Pattern.compile(
                                    "CREATE TABLE `" + table + "` \\(.*?\\) ENGINE=.*?;",
                                    Pattern.DOTALL)
                            .matcher(schema);
            assertTrue(match.find());
            jdbc.execute(match.group());
        }
        for (String migration :
                List.of(
                        "tj_user/V004__public_account_privacy.sql",
                        "tj_user/V005__privacy_tasks.sql",
                        "common/V006__privacy_domain_execution.sql")) {
            try (var connection = source.getConnection()) {
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
                        connection,
                        new org.springframework.core.io.FileSystemResource(
                                root.resolve("deploy/acceptance/migrations/" + migration)));
            }
        }
        jdbc.update(
                "INSERT INTO user(id,username,cell_phone,password,type,status,update_time) VALUES(701,'student701','13900000701','synthetic-secret-password-hash',2,1,'2020-01-01'),(702,'student702','13900000702','synthetic-other-secret',2,1,'2020-01-01'),(1,'admin1','13900000001','synthetic-admin-secret',1,1,'2020-01-01')");
        jdbc.update(
                "INSERT INTO user_detail(id,type,name,email,role_id,update_time) VALUES(701,2,'own student','own@example.test',2,'2020-01-01'),(702,2,'other student','other@example.test',2,'2020-01-01')");
        var definition =
                json.readValue(
                        Files.readString(
                                root.resolve(
                                        "tj-user/src/main/resources/META-INF/tianji/privacy/user.json")),
                        PrivacyDomain.Definition.class);
        var userDomain =
                new PrivacyDomain(
                        definition,
                        jdbc,
                        new TransactionTemplate(new DataSourceTransactionManager(source)),
                        json);
        List<PrivacyDomainClient> clients = new ArrayList<>();
        for (String alias : PrivacyTaskService.DOMAINS) {
            var boundary = new Boundary(alias, "user".equals(alias) ? userDomain : null);
            boundaries.put(alias, boundary);
            clients.add(boundary);
        }
        policy = temporary.resolve("retention.json");
        Files.writeString(
                policy,
                "{\"version\":\"reviewed-v1\",\"rules\":{\"user.profile\":{\"days\":1,\"action\":\"ANONYMIZE\"},\"user.details\":{\"days\":1,\"action\":\"ANONYMIZE\"}}}");
        var env =
                new MockEnvironment()
                        .withProperty("tj.privacy.worker.enabled", "false")
                        .withProperty("tj.privacy.archive.provider", "LOCAL")
                        .withProperty(
                                "tj.privacy.archive.directory",
                                temporary.resolve("archives").toString())
                        .withProperty("tj.privacy.retention-policy-file", policy.toString());
        archives = new PrivacyArchiveStore(env);
        tasks =
                new PrivacyTaskService(
                        jdbc,
                        new DataSourceTransactionManager(source),
                        json,
                        clients,
                        archives,
                        env);
        UserContext.setUser(701L);
        UserContext.setRole(2L);
    }

    @AfterEach
    void close() {
        UserContext.removeUser();
        if (archives != null) archives.close();
    }

    private String request(String kind) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO user_privacy_request(id,user_id,kind,retained_domains) VALUES(?,701,?,'all reviewed domains')",
                id,
                kind);
        return id;
    }

    private String create(String kind) {
        return tasks.create(request(kind), UUID.randomUUID().toString()).get("id").toString();
    }

    private Map<String, Object> drain(String id, String expected) {
        for (int step = 0; step < 200; step++) {
            var task = tasks.view(id);
            if (!"PENDING".equals(task.get("status"))) {
                assertEquals(expected, task.get("status"), task.toString());
                return task;
            }
            tasks.process(id);
        }
        fail("Privacy task did not reach " + expected);
        return null;
    }

    @Test
    void completeExportContainsAllDomainResultsAndOnlyOwnedReviewedFields() throws Exception {
        String request = request("EXPORT"), key = UUID.randomUUID().toString();
        String id = tasks.create(request, key).get("id").toString();
        assertEquals(id, tasks.create(request, key).get("id"));
        assertThrows(BadRequestException.class, () -> tasks.download(id));
        var ready = drain(id, "READY");
        assertEquals(
                13L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM privacy_task_domain WHERE task_id=? AND status='DONE'",
                        Long.class,
                        id));
        byte[] bytes;
        try (var input = tasks.download(id)) {
            bytes = input.readAllBytes();
        }
        assertEquals(
                ready.get("artifact_sha256"),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        Map<String, String> files = new TreeMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                files.put(
                        entry.getName(),
                        new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        assertTrue(files.containsKey("manifest.json"));
        var manifest = json.readValue(files.get("manifest.json"), Map.class);
        assertEquals(13, ((List<?>) manifest.get("domains")).size());
        String exported = String.join("\n", files.values());
        assertTrue(exported.contains("student701"));
        assertFalse(exported.contains("student702"));
        assertFalse(exported.contains("synthetic-secret-password-hash"));
        assertFalse(exported.contains("other@example.test"));
        UserContext.setUser(702L);
        assertThrows(ForbiddenException.class, () -> tasks.view(id));
        assertThrows(ForbiddenException.class, () -> tasks.download(id));
        UserContext.setUser(701L);
        jdbc.update(
                "UPDATE user_privacy_request SET status='COMPLETED',resolution='delivered to verified requester',evidence_reference='synthetic-delivery-receipt',reviewed_by=1,reviewed_at=NOW(3) WHERE id=?",
                request);
        assertFalse(tasks.view(id).containsKey("delivery"));
        UserContext.setUser(1L);
        UserContext.setRole(1L);
        var delivery = (Map<?, ?>) tasks.view(id).get("delivery");
        assertEquals("synthetic-delivery-receipt", delivery.get("evidence_reference"));
        assertEquals("COMPLETED", delivery.get("status"));
        UserContext.setUser(701L);
        UserContext.setRole(2L);
        jdbc.update("UPDATE privacy_task SET expires_at=NOW(3)-INTERVAL 1 SECOND WHERE id=?", id);
        assertThrows(BadRequestException.class, () -> tasks.download(id));
    }

    @Test
    void failedRemoteDomainDoesNotPublishPartialSuccessAndCanResume() throws Exception {
        String id = create("EXPORT");
        boundaries.get("exam").unavailable = true;
        var failed = drain(id, "FAILED");
        assertEquals("DOMAIN_PROCESSING_FAILED", failed.get("error_code"));
        assertNull(failed.get("artifact_sha256"));
        assertThrows(BadRequestException.class, () -> tasks.download(id));
        boundaries.get("exam").unavailable = false;
        long version = ((Number) failed.get("version")).longValue();
        assertThrows(ConflictException.class, () -> tasks.retry(id, version - 1));
        tasks.retry(id, version);
        drain(id, "READY");
        try (var input = tasks.download(id)) {
            assertTrue(input.readAllBytes().length > 0);
        }
    }

    @Test
    void cleanupRequiresDisabledAccountReviewedPlanCurrentPolicyAndVersion() throws Exception {
        UserContext.setUser(1L);
        UserContext.setRole(1L);
        String request = request("DELETE");
        assertThrows(ConflictException.class, () -> tasks.create(request, "cleanup-one"));
        jdbc.update("UPDATE user SET status=0,update_time='2020-01-01' WHERE id=701");
        String id = tasks.create(request, "cleanup-one").get("id").toString();
        var plan = drain(id, "APPROVAL_REQUIRED");
        assertEquals(
                "own@example.test",
                jdbc.queryForObject("SELECT email FROM user_detail WHERE id=701", String.class));
        long version = ((Number) plan.get("version")).longValue();
        String hash = plan.get("plan_hash").toString();
        assertThrows(ConflictException.class, () -> tasks.approve(id, version - 1, hash));
        assertThrows(ConflictException.class, () -> tasks.approve(id, version, "wrong-hash"));
        Files.writeString(policy, Files.readString(policy).replace("reviewed-v1", "reviewed-v2"));
        assertThrows(ConflictException.class, () -> tasks.approve(id, version, hash));
        Files.writeString(policy, Files.readString(policy).replace("reviewed-v2", "reviewed-v1"));
        jdbc.update("UPDATE user SET status=1 WHERE id=701");
        assertThrows(ConflictException.class, () -> tasks.approve(id, version, hash));
        jdbc.update("UPDATE user SET status=0,update_time='2020-01-01' WHERE id=701");
        tasks.approve(id, version, hash);
        var completed = drain(id, "COMPLETED");
        assertEquals(hash, completed.get("plan_hash"));
        assertEquals(
                13L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM privacy_task_domain WHERE task_id=? AND inventory IS NOT NULL",
                        Long.class,
                        id));
        assertNull(jdbc.queryForObject("SELECT email FROM user_detail WHERE id=701", String.class));
        assertEquals(
                "other@example.test",
                jdbc.queryForObject("SELECT email FROM user_detail WHERE id=702", String.class));
        assertEquals(
                "student702",
                jdbc.queryForObject("SELECT username FROM user WHERE id=702", String.class));
        assertTrue(
                jdbc.queryForObject("SELECT cell_phone FROM user WHERE id=701", String.class)
                        .startsWith("x"));
    }

    @Test
    void aStaleArchiveWriteCannotReplaceTheCommittedArtifact() throws Exception {
        String id = create("EXPORT");
        var ready = drain(id, "READY");
        String committed = ready.get("artifact_sha256").toString();
        byte[] expected;
        try (var input = tasks.download(id)) {
            expected = input.readAllBytes();
        }
        Path stale = archives.workingFile(id);
        byte[] oldBytes =
                "synthetic stale worker archive".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(stale, oldBytes);
        String staleHash =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(oldBytes));
        archives.save(id, staleHash, stale);
        assertNotEquals(committed, staleHash);
        try (var input = tasks.download(id)) {
            assertArrayEquals(expected, input.readAllBytes());
        }
    }

    @Test
    void unknownPolicyDomainCannotBeSilentlyIgnored() throws Exception {
        Files.writeString(
                policy,
                "{\"version\":\"reviewed-v1\",\"rules\":{\"learnng.notes\":{\"days\":1,\"action\":\"ANONYMIZE\"}}}");
        assertThrows(BadRequestException.class, () -> create("EXPORT"));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM privacy_task", Long.class));
    }

    @Test
    void durableTaskMetricsTrackPendingFailedApprovalAndPreserveTheLastSuccessfulSample() {
        var meters = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        try {
            var metrics = new PrivacyTaskMetrics(jdbc, meters);
            String timestamp = "tj.privacy.metrics.last.success.timestamp.seconds";
            assertEquals(0, meters.get(timestamp).gauge().value());
            String id = create("EXPORT");
            jdbc.update(
                    "UPDATE privacy_task SET created_at=NOW(3)-INTERVAL 60 SECOND WHERE id=?", id);
            metrics.refresh();
            assertEquals(1, meters.get("tj.privacy.tasks.pending").gauge().value());
            assertEquals(0, meters.get("tj.privacy.tasks.failed").gauge().value());
            assertTrue(meters.get("tj.privacy.tasks.oldest.seconds").gauge().value() >= 59);
            assertTrue(meters.get(timestamp).gauge().value() > 0);
            jdbc.update(
                    "UPDATE privacy_task SET status='FAILED',error_code='DOMAIN_PROCESSING_FAILED' WHERE id=?",
                    id);
            metrics.refresh();
            assertEquals(0, meters.get("tj.privacy.tasks.pending").gauge().value());
            assertEquals(1, meters.get("tj.privacy.tasks.failed").gauge().value());
            jdbc.update("UPDATE privacy_task SET status='APPROVAL_REQUIRED' WHERE id=?", id);
            metrics.refresh();
            assertEquals(0, meters.get("tj.privacy.tasks.failed").gauge().value());
            assertEquals(1, meters.get("tj.privacy.tasks.approval.required").gauge().value());
            double previous = meters.get(timestamp).gauge().value();
            jdbc.execute("RENAME TABLE privacy_task TO privacy_task_sampling_unavailable");
            try {
                assertThrows(org.springframework.dao.DataAccessException.class, metrics::refresh);
                assertEquals(previous, meters.get(timestamp).gauge().value());
                assertEquals(1, meters.get("tj.privacy.tasks.approval.required").gauge().value());
            } finally {
                jdbc.execute("RENAME TABLE privacy_task_sampling_unavailable TO privacy_task");
            }
        } finally {
            meters.close();
        }
    }

    @Test
    void rejectedRequestCannotApproveResumePublishOrDownloadAnOldTask() throws Exception {
        UserContext.setUser(1L);
        UserContext.setRole(1L);
        jdbc.update("UPDATE user SET status=0 WHERE id=701");
        String cleanup = create("DELETE");
        var plan = drain(cleanup, "APPROVAL_REQUIRED");
        jdbc.update(
                "UPDATE user_privacy_request SET status='REJECTED' WHERE id=?",
                plan.get("request_id"));
        assertThrows(
                ConflictException.class,
                () ->
                        tasks.approve(
                                cleanup,
                                ((Number) plan.get("version")).longValue(),
                                plan.get("plan_hash").toString()));
        assertEquals(
                "own@example.test",
                jdbc.queryForObject("SELECT email FROM user_detail WHERE id=701", String.class));
        jdbc.update("UPDATE user SET status=1 WHERE id=701");
        String export = create("EXPORT");
        var ready = drain(export, "READY");
        jdbc.update(
                "UPDATE user_privacy_request SET status='REJECTED' WHERE id=?",
                ready.get("request_id"));
        assertThrows(ConflictException.class, () -> tasks.download(export));
        jdbc.update("UPDATE privacy_task SET status='FAILED' WHERE id=?", export);
        assertThrows(
                ConflictException.class,
                () -> tasks.retry(export, ((Number) ready.get("version")).longValue()));
        jdbc.update("UPDATE privacy_task SET status='PENDING' WHERE id=?", export);
        tasks.process(export);
        assertEquals("PENDING", tasks.view(export).get("status"));
        assertEquals("DOMAIN_PROCESSING_FAILED", tasks.view(export).get("error_code"));
        assertEquals(
                0L,
                jdbc.queryForObject("SELECT COUNT(*) FROM privacy_domain_execution", Long.class));
    }
}
