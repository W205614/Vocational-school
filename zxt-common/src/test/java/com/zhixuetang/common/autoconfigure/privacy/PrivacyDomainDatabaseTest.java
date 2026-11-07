package com.zhixuetang.common.autoconfigure.privacy;

import com.zhixuetang.common.exceptions.BadRequestException;
import com.zhixuetang.common.exceptions.ConflictException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Executes the actual reviewed SQL and domain implementation against MySQL 8. */
@EnabledIfEnvironmentVariable(named = "ACCEPTANCE_DB_PASSWORD", matches = ".+")
class PrivacyDomainDatabaseTest {
    private static final Map<String, PrivacyDomain> domains = new TreeMap<>();
    private static final Map<String, JdbcTemplate> databases = new TreeMap<>();
    private static Path root;

    @BeforeAll
    static void setup() throws Exception {
        root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("deploy/compact/schema.sql"))) root = root.getParent();
        String port = System.getenv().getOrDefault("ACCEPTANCE_DB_PORT", "23316");
        String url =
                "jdbc:mysql://127.0.0.1:"
                        + port
                        + "/?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true";
        var bootstrap =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                url, "root", System.getenv("ACCEPTANCE_DB_PASSWORD")));
        String schema = Files.readString(root.resolve("deploy/compact/schema.sql"));
        var json = JsonMapper.builder().build();
        String run = UUID.randomUUID().toString().substring(0, 8);
        List<Path> inventories = new ArrayList<>();
        for (String alias :
                List.of(
                        "auth",
                        "user",
                        "trade",
                        "pay",
                        "promotion",
                        "course",
                        "learning",
                        "exam",
                        "remark",
                        "media",
                        "search",
                        "message")) {
            String module =
                    switch (alias) {
                        case "auth", "pay", "message" ->
                                "zxt-" + alias + "/zxt-" + alias + "-service";
                        default -> "zxt-" + alias;
                    };
            inventories.add(
                    root.resolve(
                            module
                                    + "/src/main/resources/META-INF/zhixuetang/privacy/"
                                    + alias
                                    + ".json"));
        }
        assertEquals(12, inventories.size(), "Every database-owning module needs a real inventory");
        for (Path inventory : inventories) {
            var definition =
                    json.readValue(Files.readString(inventory), PrivacyDomain.Definition.class);
            String alias = definition.domain();
            assertTrue(alias.matches("[a-z]+"));
            String name = "acceptance_privacy_" + alias + "_" + run;
            bootstrap.execute("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4");
            var source =
                    new DriverManagerDataSource(
                            url.replace("/?", "/" + name + "?"),
                            "root",
                            System.getenv("ACCEPTANCE_DB_PASSWORD"));
            var jdbc = new JdbcTemplate(source);
            int start = schema.indexOf("USE `tj_" + alias + "`;");
            assertTrue(start >= 0, "Missing schema for " + alias);
            int end = schema.indexOf("\nUSE `", start + 5);
            String section = schema.substring(start, end < 0 ? schema.length() : end);
            var tables =
                    Pattern.compile("CREATE TABLE `[^`]+` \\(.*?\\) ENGINE=.*?;", Pattern.DOTALL)
                            .matcher(section);
            int created = 0;
            while (tables.find()) {
                jdbc.execute(tables.group());
                created++;
            }
            assertTrue(created > 0, "No actual baseline tables created for " + alias);
            script(jdbc, root.resolve("deploy/acceptance/migrations/common/V004__admin_audit.sql"));
            script(
                    jdbc,
                    root.resolve(
                            "deploy/acceptance/migrations/common/V006__privacy_domain_execution.sql"));
            if (alias.equals("user")) {
                script(
                        jdbc,
                        root.resolve(
                                "deploy/acceptance/migrations/tj_user/V004__public_account_privacy.sql"));
                script(
                        jdbc,
                        root.resolve(
                                "deploy/acceptance/migrations/tj_user/V005__privacy_tasks.sql"));
            }
            if (alias.equals("auth")) {
                script(
                        jdbc,
                        root.resolve("deploy/acceptance/migrations/tj_auth/V003__sessions.sql"));
                script(
                        jdbc,
                        root.resolve(
                                "deploy/acceptance/migrations/tj_auth/V005__administrator_mfa.sql"));
            }
            if (alias.equals("learning"))
                createMigrationTables(jdbc, "tj_learning/V008__learning_motivation.sql");
            if (alias.equals("promotion"))
                createMigrationTables(jdbc, "tj_promotion/V004__season_reward_delivery.sql");
            if (alias.equals("media"))
                createMigrationTables(jdbc, "tj_media/V005__private_cloud_media_tasks.sql");
            domains.put(
                    alias,
                    new PrivacyDomain(
                            definition,
                            jdbc,
                            new TransactionTemplate(new DataSourceTransactionManager(source)),
                            json));
            databases.put(alias, jdbc);
        }
    }

    private static void script(JdbcTemplate jdbc, Path path) throws Exception {
        var resource = new org.springframework.core.io.FileSystemResource(path);
        try (var connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
                    connection, resource);
        }
    }

    private static void createMigrationTables(JdbcTemplate jdbc, String relative) throws Exception {
        String source = Files.readString(root.resolve("deploy/acceptance/migrations/" + relative));
        var statements =
                Pattern.compile(
                                "CREATE TABLE(?: IF NOT EXISTS)?\\s+`?[a-z_]+`?\\s*\\(.*?;",
                                Pattern.DOTALL)
                        .matcher(source);
        while (statements.find())
            jdbc.execute(
                    statements
                            .group()
                            .replaceFirst(
                                    "CREATE TABLE(?: IF NOT EXISTS)?",
                                    "CREATE TABLE IF NOT EXISTS"));
    }

    @Test
    void everyReviewedSelectCountEligibleAndCleanupUsesTheActualSchema() {
        for (var entry : domains.entrySet()) {
            var domain = entry.getValue();
            var inventory = domain.inventory();
            Map<String, String> cutoffs = new TreeMap<>();
            for (var dataset : inventory.datasets()) {
                var page = domain.export(990001L, dataset.name(), 0);
                assertEquals(entry.getKey(), page.domain());
                assertTrue(page.rows().isEmpty(), entry.getKey() + "." + dataset.name());
                assertNull(page.nextOffset());
                assertNull(dataset.select(), "Never expose internal SQL through inventory");
                if (dataset.cleanup() != null) cutoffs.put(dataset.name(), "2021-01-01T00:00:00Z");
            }
            assertEquals(inventory.datasets().size(), domain.plan(990001L, cutoffs).size());
            var request =
                    new PrivacyDomain.Cleanup(
                            UUID.randomUUID().toString(), 990001L, "test-v1", cutoffs);
            assertEquals(
                    domain.cleanup(request),
                    domain.cleanup(request),
                    "Execution receipts must make replay stable");
        }
    }

    @Test
    void anonymizationIsScopedIdempotentAndRetainsTheOtherUsersData() {
        JdbcTemplate auth = databases.get("auth");
        auth.update(
                "INSERT INTO login_record(user_id,cell_phone,ipv4,login_time) VALUES(731,'13900000731','192.0.2.1','2020-01-01'),(732,'13900000732','192.0.2.2','2020-01-01')");
        exercise("auth", Map.of("logins", "2021-01-01T00:00:00Z"));
        assertEquals(
                "",
                auth.queryForObject(
                        "SELECT ipv4 FROM login_record WHERE user_id=731", String.class));
        assertEquals(
                "192.0.2.2",
                auth.queryForObject(
                        "SELECT ipv4 FROM login_record WHERE user_id=732", String.class));

        JdbcTemplate user = databases.get("user");
        user.update(
                "INSERT INTO user(id,username,cell_phone,password,type,status,update_time) VALUES(731,'own_student','13900000731','synthetic',2,0,'2020-01-01'),(732,'other_student','13900000732','synthetic',2,1,'2020-01-01')");
        user.update(
                "INSERT INTO user_detail(id,type,name,email,role_id,update_time) VALUES(731,2,'own name','own@example.test',2,'2020-01-01'),(732,2,'other name','other@example.test',2,'2020-01-01')");
        exercise(
                "user",
                Map.of("details", "2021-01-01T00:00:00Z", "profile", "2021-01-01T00:00:00Z"));
        assertNull(user.queryForObject("SELECT email FROM user_detail WHERE id=731", String.class));
        assertEquals(
                "other@example.test",
                user.queryForObject("SELECT email FROM user_detail WHERE id=732", String.class));
        assertEquals(
                "x00000000000002DB",
                user.queryForObject("SELECT cell_phone FROM user WHERE id=731", String.class));
        assertEquals(
                "13900000732",
                user.queryForObject("SELECT cell_phone FROM user WHERE id=732", String.class));

        JdbcTemplate learning = databases.get("learning");
        learning.update(
                "INSERT INTO course_note(id,user_id,course_id,content,updated_at) VALUES(731,731,1,'own note','2020-01-01'),(732,732,1,'other note','2020-01-01')");
        learning.update(
                "INSERT INTO interaction_question(id,title,description,course_id,chapter_id,section_id,user_id,update_time) VALUES(731,'own question','own description',1,1,1,731,'2020-01-01'),(732,'other question','other description',1,1,1,732,'2020-01-01')");
        learning.update(
                "INSERT INTO interaction_reply(id,question_id,user_id,content,update_time) VALUES(731,731,731,'own reply','2020-01-01'),(732,731,732,'other reply','2020-01-01')");
        exercise(
                "learning",
                Map.of(
                        "notes",
                        "2021-01-01T00:00:00Z",
                        "questions",
                        "2021-01-01T00:00:00Z",
                        "replies",
                        "2021-01-01T00:00:00Z"));
        assertEquals(
                "[已清理]",
                learning.queryForObject(
                        "SELECT content FROM course_note WHERE id=731", String.class));
        assertEquals(
                "other note",
                learning.queryForObject(
                        "SELECT content FROM course_note WHERE id=732", String.class));
        assertEquals(
                "[已清理]",
                learning.queryForObject(
                        "SELECT content FROM interaction_reply WHERE id=731", String.class));
        assertEquals(
                "other reply",
                learning.queryForObject(
                        "SELECT content FROM interaction_reply WHERE id=732", String.class));
        assertEquals(
                "已注销学员的提问",
                learning.queryForObject(
                        "SELECT title FROM interaction_question WHERE id=731", String.class));
        assertEquals(
                "other question",
                learning.queryForObject(
                        "SELECT title FROM interaction_question WHERE id=732", String.class));

        JdbcTemplate message = databases.get("message");
        message.update(
                "INSERT INTO user_inbox(id,user_id,title,content,push_time,expire_time) VALUES(731,731,'own title','own message','2020-01-01','2030-01-01'),(732,732,'other title','other message','2020-01-01','2030-01-01')");
        exercise("message", Map.of("inbox", "2021-01-01T00:00:00Z"));
        assertEquals(
                "[已清理]",
                message.queryForObject(
                        "SELECT content FROM user_inbox WHERE id=731", String.class));
        assertEquals(
                "other message",
                message.queryForObject(
                        "SELECT content FROM user_inbox WHERE id=732", String.class));
    }

    private void exercise(String alias, Map<String, String> cutoffs) {
        PrivacyDomain domain = domains.get(alias);
        Map<String, Object> plan = domain.plan(731L, cutoffs);
        for (String dataset : cutoffs.keySet()) {
            assertEquals(1L, ((Map<?, ?>) plan.get(dataset)).get("eligible"));
            assertEquals(1, domain.export(731L, dataset, 0).rows().size());
        }
        String id = UUID.randomUUID().toString();
        var request = new PrivacyDomain.Cleanup(id, 731L, "reviewed-v1", cutoffs);
        var result = domain.cleanup(request);
        assertEquals(result, domain.cleanup(request));
        assertThrows(
                ConflictException.class,
                () -> domain.cleanup(new PrivacyDomain.Cleanup(id, 732L, "reviewed-v1", cutoffs)));
        assertThrows(
                BadRequestException.class,
                () -> domain.plan(731L, Map.of("unreviewed", "2021-01-01T00:00:00Z")));
    }
}
