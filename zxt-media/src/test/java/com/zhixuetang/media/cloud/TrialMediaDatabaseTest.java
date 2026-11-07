package com.zhixuetang.media.cloud;

import com.zhixuetang.common.utils.UserContext;
import com.zhixuetang.media.storage.local.LocalObjectStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real isolated MySQL plus real local bytes/transcoder. Does not access the daily deployment. */
class TrialMediaDatabaseTest {
    static JdbcTemplate jdbc;
    @TempDir Path directory;
    TrialMediaJobs jobs;
    LocalObjectStore local;
    MockEnvironment environment;
    String file;

    @BeforeAll
    static void schema() throws Exception {
        String password =
                Objects.requireNonNull(
                        System.getenv("ACCEPTANCE_DB_PASSWORD"),
                        "Isolated database credentials required");
        String url =
                "jdbc:mysql://127.0.0.1:"
                        + Objects.requireNonNullElse(System.getenv("ACCEPTANCE_DB_PORT"), "23316")
                        + "/";
        var admin = new JdbcTemplate(new DriverManagerDataSource(url, "root", password));
        admin.execute("CREATE DATABASE IF NOT EXISTS acceptance_media_trial");
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                url
                                        + "acceptance_media_trial?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true",
                                "root",
                                password));
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS media(id BIGINT PRIMARY KEY,file_id VARCHAR(100),size BIGINT,deleted INT NOT NULL DEFAULT 0)");
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS cloud_media_upload(id CHAR(36) PRIMARY KEY,state VARCHAR(16),object_key VARCHAR(512),object_version VARCHAR(256),object_etag VARCHAR(128),output_sha256 CHAR(64),output_size BIGINT)");
        if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='media_trial'",
                        Integer.class)
                == 0) {
            Path root = Path.of("").toAbsolutePath();
            while (root != null
                    && !Files.isRegularFile(
                            root.resolve(
                                    "deploy/acceptance/migrations/tj_media/V006__trial_media.sql")))
                root = root.getParent();
            assertNotNull(root);
            jdbc.execute(
                    Files.readString(
                            root.resolve(
                                    "deploy/acceptance/migrations/tj_media/V006__trial_media.sql")));
        }
    }

    @BeforeEach
    void prepare() throws Exception {
        jdbc.update("DELETE FROM media_trial");
        jdbc.update("DELETE FROM media");
        jdbc.update("DELETE FROM cloud_media_upload");
        environment =
                new MockEnvironment()
                        .withProperty(
                                "zxt.local-storage.directory",
                                directory.resolve("objects").toString())
                        .withProperty("zxt.local-storage.enabled", "true")
                        .withProperty(
                                "zxt.media.trial-work-directory",
                                directory.resolve("work").toString());
        environment.setActiveProfiles("acceptance");
        local = new LocalObjectStore(environment, JsonMapper.builder().build());
        var beans = new StaticListableBeanFactory(Map.of("local", local));
        jobs =
                new TrialMediaJobs(
                        jdbc,
                        beans.getBeanProvider(LocalObjectStore.class),
                        beans.getBeanProvider(CloudObjectStore.class),
                        environment);
        UserContext.setUser(7L);
        UserContext.setRole(1L);
        Path source = Path.of(getClass().getResource("/media-probe/recorder.webm").toURI());
        try (var input = Files.newInputStream(source)) {
            var saved =
                    local.uploadMedia(
                            "source.webm", input, Files.size(source), "trial-database-test");
            file = saved.getFileId();
            jdbc.update("INSERT INTO media(id,file_id,size) VALUES(22,?,?)", file, saved.getSize());
        }
    }

    @AfterEach
    void close() {
        jobs.close();
        UserContext.removeUser();
    }

    @Test
    void durableClipIsIdempotentAndExpiredLeaseCanBeRecovered() throws Exception {
        var first = jobs.request(22, 2);
        String id = first.get("id").toString();
        assertEquals("PENDING", first.get("state"));
        assertEquals(id, jobs.request(22, 2).get("id"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM media_trial", Integer.class));
        jdbc.update(
                "UPDATE media_trial SET state='PROCESSING',attempts=1,lease_token='old',lease_until=NOW(3)-INTERVAL 1 SECOND WHERE id=?",
                id);
        jobs.process(id);
        var ready = jobs.task(id);
        assertEquals("READY", ready.get("state"));
        assertEquals(2, ready.get("attempts"));
        assertTrue(((Number) ready.get("output_duration")).floatValue() <= 2.1f);
        assertTrue(Files.isRegularFile(local.trialPath(id)));
        assertEquals(CloudMediaProcessor.sha256(local.trialPath(id)), ready.get("output_sha256"));
        assertNotEquals(file, ready.get("object_key"));
        jobs.process(id);
        assertEquals(2, jobs.task(id).get("attempts"));
        assertNotEquals(id, jobs.request(22, 3).get("id"));
        jobs.removeDerived(file);
        assertFalse(Files.exists(local.trialPath(id)));
        assertEquals("REMOVED", jobs.task(id).get("state"));
    }

    @Test
    void changedSourceCannotBePublishedUnderTheOldTrialIdentity() {
        String id = jobs.request(22, 2).get("id").toString();
        jdbc.update("UPDATE media SET file_id=? WHERE id=22", "b".repeat(64) + ".mp4");
        jobs.process(id);
        var failed = jobs.task(id);
        assertEquals("FAILED", failed.get("state"));
        assertEquals("TRIAL_SOURCE_CHANGED", failed.get("error_code"));
        assertFalse(Files.exists(local.trialPath(id)));
    }

    @Test
    void cloudWorkerUsesPinnedInputAndPublishesAnIndependentPrivateObject() throws Exception {
        jobs.close();
        var cloud = mock(CloudObjectStore.class);
        var beans = new StaticListableBeanFactory(Map.of("cloud", cloud));
        environment.withProperty("zxt.cloud-media.role", "worker");
        jobs =
                new TrialMediaJobs(
                        jdbc,
                        beans.getBeanProvider(LocalObjectStore.class),
                        beans.getBeanProvider(CloudObjectStore.class),
                        environment);
        Path source = Path.of(getClass().getResource("/media-probe/tiny.mp4").toURI());
        byte[] bytes = Files.readAllBytes(source);
        String task = UUID.randomUUID().toString();
        jdbc.update("UPDATE media SET file_id=?,size=? WHERE id=22", "cos:" + task, bytes.length);
        jdbc.update(
                "INSERT INTO cloud_media_upload(id,state,object_key,object_version,object_etag,output_sha256,output_size) VALUES(?,'READY','ready/original.mp4','original-version','original-etag',?,?)",
                task,
                CloudMediaProcessor.sha256(source),
                bytes.length);
        when(cloud.read(
                        "ready/original.mp4",
                        "original-version",
                        "original-etag",
                        0,
                        bytes.length - 1))
                .thenReturn(new java.io.ByteArrayInputStream(bytes));
        when(cloud.putReady(anyString(), any()))
                .thenAnswer(
                        call ->
                                new CloudObjectStore.ObjectInfo(
                                        Files.size(call.getArgument(1)),
                                        "video/mp4",
                                        "clip-version",
                                        "clip-etag"));
        String id = jobs.request(22, 1).get("id").toString();
        jobs.process(id);
        var ready = jobs.task(id);
        assertEquals("READY", ready.get("state"));
        assertTrue(ready.get("object_key").toString().startsWith("ready/trials/" + id + "/"));
        assertEquals("clip-version", ready.get("object_version"));
        assertEquals("clip-etag", ready.get("object_etag"));
        assertTrue(((Number) ready.get("output_duration")).floatValue() <= 1.1f);
        verify(cloud)
                .read(
                        "ready/original.mp4",
                        "original-version",
                        "original-etag",
                        0,
                        bytes.length - 1);
        verify(cloud).putReady(startsWith("ready/trials/" + id + "/"), any());
    }
}
