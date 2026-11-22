package com.zhixuetang.course.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhixuetang.common.exceptions.ConflictException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ACCEPTANCE_DB_PASSWORD", matches = ".+")
class CourseMediaDeletionGuardDatabaseTest {
    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static CourseMediaDeletionGuard guard;

    @BeforeAll
    static void setup() {
        String url = "jdbc:mysql://127.0.0.1:" + System.getenv().getOrDefault("ACCEPTANCE_DB_PORT", "23316");
        String password = System.getenv("ACCEPTANCE_DB_PASSWORD");
        new JdbcTemplate(new DriverManagerDataSource(url + "/", "root", password))
                .execute("CREATE DATABASE IF NOT EXISTS acceptance_course_media_deletion");
        var source = new DriverManagerDataSource(url + "/acceptance_course_media_deletion", "root", password);
        jdbc = new JdbcTemplate(source);
        for (String suffix : List.of("", "_draft")) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS course" + suffix
                    + "(id BIGINT PRIMARY KEY,name VARCHAR(100),status INT,deleted INT DEFAULT 0)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS course_catalogue" + suffix
                    + "(id BIGINT PRIMARY KEY,name VARCHAR(100),course_id BIGINT,media_id BIGINT,type INT DEFAULT 2,deleted INT DEFAULT 0)");
        }
        if (jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema=DATABASE() AND table_name='course_media_deletion_guard'
                """, Integer.class) == 0) {
            Path migration = Path.of("deploy/acceptance/migrations/tj_course/V005__course_media_deletion_guard.sql");
            Path root = Path.of("").toAbsolutePath();
            while (root != null && !Files.isRegularFile(root.resolve(migration))) root = root.getParent();
            assertNotNull(root, "Course migration must be loaded from the repository");
            new ResourceDatabasePopulator(new FileSystemResource(root.resolve(migration))).execute(source);
        }
        var manager = new DataSourceTransactionManager(source);
        tx = new TransactionTemplate(manager);
        var factory = new ProxyFactory(new CourseMediaDeletionGuard(jdbc));
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        guard = (CourseMediaDeletionGuard) factory.getProxy();
    }

    @Test
    void aReferencedItemPreventsReservationOfTheEntireBatchIncludingDownShelfAndDraftReferences() {
        long media = IdWorker.getId(), unused = media + 1;
        insertReference(media, false, 3);
        insertReference(media, true, 1);

        var references = guard.references(media);
        assertEquals(List.of(3, 1), references.stream().map(row -> row.courseStatus()).toList());
        assertFalse(references.getFirst().draft());
        assertTrue(references.getLast().draft());
        assertFalse(guard.reserveDeletion(List.of(unused, media)));
        tx.executeWithoutResult(status -> guard.requireCanReference(List.of(unused)));
    }

    @Test
    void aCompletedReservationIsRepeatableAndPreventsReattachingTheMedia() {
        long media = IdWorker.getId();
        assertTrue(guard.reserveDeletion(List.of(media)));
        assertTrue(guard.reserveDeletion(List.of(media)));
        assertThrows(ConflictException.class,
                () -> tx.executeWithoutResult(status -> guard.requireCanReference(List.of(media))));
    }

    @Test
    void deletionObservesPendingReferenceAfterThatTransactionCommits() throws Exception {
        long media = IdWorker.getId();
        var referenceWritten = new CountDownLatch(1);
        var commitReference = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var reference = pool.submit(() -> tx.executeWithoutResult(status -> {
                guard.requireCanReference(List.of(media));
                insertReference(media, true, 1);
                referenceWritten.countDown();
                try {
                    assertTrue(commitReference.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            }));
            try {
                assertTrue(referenceWritten.await(10, TimeUnit.SECONDS));
                var deleting = pool.submit(() -> guard.reserveDeletion(List.of(media)));
                assertThrows(java.util.concurrent.TimeoutException.class,
                        () -> deleting.get(500, TimeUnit.MILLISECONDS));
                commitReference.countDown();
                reference.get(10, TimeUnit.SECONDS);
                assertFalse(deleting.get(10, TimeUnit.SECONDS));
            } finally {
                commitReference.countDown();
            }
        }
    }

    private static void insertReference(long mediaId, boolean draft, int status) {
        long course = IdWorker.getId(), section = IdWorker.getId();
        String suffix = draft ? "_draft" : "";
        jdbc.update("INSERT INTO course" + suffix + "(id,name,status) VALUES(?,'Fixture course',?)", course, status);
        jdbc.update("INSERT INTO course_catalogue" + suffix
                + "(id,name,course_id,media_id) VALUES(?,'Fixture section',?,?)", section, course, mediaId);
    }
}
