package com.tianji.user.privacy;

import com.tianji.api.client.privacy.PrivacyDomainClient;
import com.tianji.common.autoconfigure.privacy.PrivacyDomain;
import com.tianji.common.exceptions.*;
import com.tianji.common.utils.UserContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;

/** A leased, one-page-per-turn coordinator. Each domain commits its own idempotent effects. */
@Service
public class PrivacyTaskService {
    public static final Set<String> DOMAINS =
            Set.of(
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
                    "message",
                    "data");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final Map<String, PrivacyDomainClient> clients;
    private final PrivacyArchiveStore archives;
    private final PrivacyRetention retention;
    private final boolean workerEnabled;

    public PrivacyTaskService(
            JdbcTemplate jdbc,
            PlatformTransactionManager manager,
            JsonMapper json,
            List<PrivacyDomainClient> clients,
            PrivacyArchiveStore archives,
            Environment env) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
        this.json = json;
        this.archives = archives;
        this.clients = new TreeMap<>();
        for (var client : clients)
            if (this.clients.put(client.domain(), client) != null)
                throw new IllegalStateException("Duplicate privacy domain");
        if (!this.clients.keySet().equals(DOMAINS))
            throw new IllegalStateException("隐私处理必须配置全部业务域");
        retention = new PrivacyRetention(env, json);
        workerEnabled = env.getProperty("tj.privacy.worker.enabled", Boolean.class, true);
    }

    public Map<String, Object> create(String requestId, String key) {
        UserContext.requireUser();
        if (requestId == null
                || !requestId.matches("[0-9a-f-]{36}")
                || key == null
                || !key.matches("[A-Za-z0-9_.:-]{1,128}"))
            throw new BadRequestException("需要申请编号和有效 Idempotency-Key");
        return tx.execute(
                status -> {
                    var rows =
                            jdbc.queryForList(
                                    "SELECT * FROM user_privacy_request WHERE id=? FOR UPDATE",
                                    requestId);
                    if (rows.isEmpty()) throw new BadRequestException("隐私申请不存在");
                    var request = rows.getFirst();
                    long owner = ((Number) request.get("user_id")).longValue();
                    authorizeOwner(owner);
                    String kind = "EXPORT".equals(request.get("kind")) ? "EXPORT" : "CLEANUP";
                    if ("CLEANUP".equals(kind)) {
                        UserContext.requireAdmin();
                        requireDeactivated(owner);
                    }
                    if (!Set.of("PENDING_REVIEW", "IN_PROGRESS").contains(request.get("status")))
                        throw new ConflictException("申请已结束");
                    var policy = retention.current();
                    for (String name : policy.rules().keySet())
                        if (!DOMAINS.contains(name.substring(0, name.indexOf('.'))))
                            throw new BadRequestException("保留规则含未知业务域");
                    String id = UUID.randomUUID().toString();
                    jdbc.update(
                            "INSERT IGNORE INTO privacy_task(id,request_id,user_id,kind,idempotency_key,policy_version,policy_hash,cutoffs) VALUES(?,?,?,?,?,?,?,?)",
                            id,
                            requestId,
                            owner,
                            kind,
                            key,
                            policy.version(),
                            retention.hash(policy),
                            json.writeValueAsString(retention.cutoffs(policy, Instant.now())));
                    var task =
                            jdbc.queryForMap(
                                    "SELECT * FROM privacy_task WHERE user_id=? AND idempotency_key=?",
                                    owner,
                                    key);
                    if (!requestId.equals(task.get("request_id")))
                        throw new ConflictException("幂等键已用于另一申请");
                    id = task.get("id").toString();
                    for (String domain : DOMAINS)
                        jdbc.update(
                                "INSERT IGNORE INTO privacy_task_domain(task_id,domain) VALUES(?,?)",
                                id,
                                domain);
                    jdbc.update(
                            "UPDATE user_privacy_request SET status='IN_PROGRESS',version=version+1 WHERE id=? AND status='PENDING_REVIEW'",
                            requestId);
                    return view(id);
                });
    }

    public Map<String, Object> list(int page, int size) {
        long user = UserContext.requireUser();
        if (page < 1 || size < 1 || size > 100) throw new BadRequestException("分页参数无效");
        boolean admin = Long.valueOf(1).equals(UserContext.getRole());
        String where = admin ? "" : " WHERE user_id=?";
        Object[] countArgs = admin ? new Object[0] : new Object[] {user};
        Object[] args =
                admin
                        ? new Object[] {size, (page - 1) * size}
                        : new Object[] {user, size, (page - 1) * size};
        return Map.of(
                "total",
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM privacy_task" + where, Long.class, countArgs),
                "items",
                jdbc.queryForList(
                        "SELECT id,request_id,user_id,kind,status,version,error_code,created_at,completed_at,expires_at FROM privacy_task"
                                + where
                                + " ORDER BY created_at DESC,id LIMIT ? OFFSET ?",
                        args));
    }

    public Map<String, Object> view(String id) {
        var task = owned(id);
        task.remove("lease_token");
        task.remove("idempotency_key");
        task.put("cutoffs", json.readValue(task.get("cutoffs").toString(), Map.class));
        var domains =
                jdbc.queryForList(
                        "SELECT domain,status,dataset_index,page_offset,inventory,result FROM privacy_task_domain WHERE task_id=? ORDER BY domain",
                        id);
        for (var domain : domains) {
            if (domain.get("result") != null)
                domain.put("result", json.readValue(domain.get("result").toString(), Object.class));
            if (domain.get("inventory") != null)
                domain.put(
                        "inventory",
                        json.readValue(domain.get("inventory").toString(), Object.class));
        }
        task.put("domains", domains);
        if (Long.valueOf(1).equals(UserContext.getRole()))
            task.put(
                    "delivery",
                    jdbc.queryForMap(
                            "SELECT status,resolution,evidence_reference,reviewed_by,reviewed_at FROM user_privacy_request WHERE id=?",
                            task.get("request_id")));
        return task;
    }

    public void approve(String id, long version, String planHash) {
        UserContext.requireAdmin();
        tx.executeWithoutResult(
                status -> {
                    var task =
                            jdbc.queryForMap(
                                    "SELECT * FROM privacy_task WHERE id=? FOR UPDATE", id);
                    requireOpenRequest(task);
                    requireDeactivated(((Number) task.get("user_id")).longValue());
                    if (!"APPROVAL_REQUIRED".equals(task.get("status"))
                            || planHash == null
                            || !planHash.equals(task.get("plan_hash")))
                        throw new ConflictException("清理计划已变化或尚未就绪");
                    if (!retention.hash(retention.current()).equals(task.get("policy_hash")))
                        throw new ConflictException("保留规则已变化，请重新创建处理任务");
                    if (jdbc.update(
                                    "UPDATE privacy_task SET status='PENDING',approved_by=?,version=version+1 WHERE id=? AND version=?",
                                    UserContext.requireUser(),
                                    id,
                                    version)
                            != 1) throw new ConflictException("任务版本已变化");
                    jdbc.update(
                            "UPDATE privacy_task_domain SET status='PENDING' WHERE task_id=?", id);
                });
    }

    public void retry(String id, long version) {
        var task = owned(id);
        if ("CLEANUP".equals(task.get("kind"))) UserContext.requireAdmin();
        if (jdbc.update(
                        "UPDATE privacy_task t JOIN user_privacy_request r ON r.id=t.request_id SET t.status='PENDING',t.attempts=0,t.error_code=NULL,t.lease_token=NULL,t.lease_until=NULL,t.next_attempt_at=NOW(3),t.version=t.version+1 WHERE t.id=? AND t.status='FAILED' AND t.version=? AND r.status='IN_PROGRESS'",
                        id,
                        version)
                != 1) throw new ConflictException("任务状态或版本已变化");
    }

    public InputStream download(String id) throws IOException {
        var task = owned(id);
        if ("REJECTED".equals(requestStatus(task))) throw new ConflictException("原隐私申请已拒绝");
        if (!"READY".equals(task.get("status"))
                || !Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT expires_at>NOW(3) FROM privacy_task WHERE id=?",
                                Boolean.class,
                                id))) throw new BadRequestException("导出文件尚未就绪或已过期");
        return archives.open(id, task.get("artifact_sha256").toString());
    }

    private Map<String, Object> owned(String id) {
        if (id == null || !id.matches("[0-9a-f-]{36}")) throw new BadRequestException("任务标识无效");
        var rows = jdbc.queryForList("SELECT * FROM privacy_task WHERE id=?", id);
        if (rows.isEmpty()) throw new BadRequestException("任务不存在");
        authorizeOwner(((Number) rows.getFirst().get("user_id")).longValue());
        return new LinkedHashMap<>(rows.getFirst());
    }

    private void authorizeOwner(long owner) {
        if (UserContext.requireUser() != owner && !Long.valueOf(1).equals(UserContext.getRole()))
            throw new ForbiddenException("无权访问其他用户的隐私任务");
    }

    private void requireDeactivated(long owner) {
        if (jdbc.queryForObject("SELECT status FROM user WHERE id=?", Integer.class, owner) != 0)
            throw new ConflictException("清理只允许处理已注销账户");
    }

    private String requestStatus(Map<String, Object> task) {
        return jdbc.queryForObject(
                "SELECT status FROM user_privacy_request WHERE id=?",
                String.class,
                task.get("request_id"));
    }

    private void requireOpenRequest(Map<String, Object> task) {
        if (!"IN_PROGRESS".equals(requestStatus(task))) throw new ConflictException("原隐私申请已结束");
    }

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        if (!workerEnabled) return;
        jdbc.update(
                "DELETE c FROM privacy_export_chunk c JOIN privacy_task t ON t.id=c.task_id WHERE t.expires_at<NOW(3)");
        var due =
                jdbc.queryForList(
                        "SELECT id FROM privacy_task WHERE status='PENDING' AND next_attempt_at<=NOW(3) AND (lease_until IS NULL OR lease_until<NOW(3)) ORDER BY next_attempt_at,id LIMIT 1");
        if (!due.isEmpty()) process(due.getFirst().get("id").toString());
    }

    public void process(String id) {
        String token = UUID.randomUUID().toString();
        if (jdbc.update(
                        "UPDATE privacy_task SET lease_token=?,lease_until=NOW(3)+INTERVAL 60 SECOND WHERE id=? AND status='PENDING' AND (lease_until IS NULL OR lease_until<NOW(3))",
                        token,
                        id)
                != 1) return;
        var task =
                jdbc.queryForMap(
                        "SELECT * FROM privacy_task WHERE id=? AND lease_token=?", id, token);
        try {
            requireOpenRequest(task);
            var pending =
                    jdbc.queryForList(
                            "SELECT * FROM privacy_task_domain WHERE task_id=? AND status<>'DONE' AND status<>'PLANNED' ORDER BY domain LIMIT 1",
                            id);
            if (pending.isEmpty()) finish(task, token);
            else step(task, pending.getFirst(), token);
            jdbc.update(
                    "UPDATE privacy_task SET lease_token=NULL,lease_until=NULL,attempts=0,error_code=NULL,next_attempt_at=NOW(3),version=version+1 WHERE id=? AND lease_token=?",
                    id,
                    token);
        } catch (Exception failure) {
            int attempts = ((Number) task.get("attempts")).intValue() + 1;
            String code =
                    failure instanceof IllegalArgumentException
                                    && "EXPORT_SIZE_LIMIT".equals(failure.getMessage())
                            ? "EXPORT_SIZE_LIMIT"
                            : "DOMAIN_PROCESSING_FAILED";
            jdbc.update(
                    "UPDATE privacy_task SET attempts=attempts+1,error_code=?,status=?,lease_token=NULL,lease_until=NULL,next_attempt_at=NOW(3)+INTERVAL ? SECOND,version=version+1 WHERE id=? AND lease_token=?",
                    code,
                    attempts >= 10 || "EXPORT_SIZE_LIMIT".equals(code) ? "FAILED" : "PENDING",
                    Math.min(300, 1 << Math.min(attempts, 8)),
                    id,
                    token);
        }
    }

    private void step(Map<String, Object> task, Map<String, Object> progress, String token) {
        String id = task.get("id").toString(), domain = progress.get("domain").toString();
        long user = ((Number) task.get("user_id")).longValue();
        var client = clients.get(domain);
        if ("CLEANUP".equals(task.get("kind"))) {
            var all = json.readValue(task.get("cutoffs").toString(), Map.class);
            Map<String, String> cutoffs = (Map<String, String>) all.getOrDefault(domain, Map.of());
            Object result;
            if (task.get("approved_by") == null) result = client.plan(user, cutoffs);
            else {
                requireDeactivated(user);
                if (!retention.hash(retention.current()).equals(task.get("policy_hash")))
                    throw new ConflictException("保留规则已变化");
                result =
                        client.cleanup(
                                new PrivacyDomain.Cleanup(
                                        id, user, task.get("policy_version").toString(), cutoffs));
            }
            saveDomain(
                    id,
                    domain,
                    token,
                    task.get("approved_by") == null ? "PLANNED" : "DONE",
                    json.writeValueAsString(result));
            return;
        }
        PrivacyDomain.Definition inventory;
        if (progress.get("inventory") == null) {
            inventory = client.inventory();
            if (!domain.equals(inventory.domain()))
                throw new IllegalStateException("Wrong privacy domain response");
            jdbc.update(
                    "UPDATE privacy_task_domain d JOIN privacy_task t ON t.id=d.task_id SET d.inventory=? WHERE d.task_id=? AND d.domain=? AND t.lease_token=?",
                    json.writeValueAsString(inventory),
                    id,
                    domain,
                    token);
        } else
            inventory =
                    json.readValue(
                            progress.get("inventory").toString(), PrivacyDomain.Definition.class);
        int index = ((Number) progress.get("dataset_index")).intValue(),
                offset = ((Number) progress.get("page_offset")).intValue();
        if (index >= inventory.datasets().size()) {
            saveDomain(
                    id,
                    domain,
                    token,
                    "DONE",
                    json.writeValueAsString(Map.of("note", inventory.note())));
            return;
        }
        String dataset = inventory.datasets().get(index).name();
        var page = client.export(user, dataset, offset);
        if (!domain.equals(page.domain()) || !dataset.equals(page.dataset()))
            throw new IllegalStateException("Wrong privacy export response");
        String payload = json.writeValueAsString(page.rows());
        int bytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        tx.executeWithoutResult(
                status -> {
                    lock(id, token);
                    long total =
                            jdbc.queryForObject(
                                    "SELECT COALESCE(SUM(bytes),0) FROM privacy_export_chunk WHERE task_id=?",
                                    Long.class,
                                    id);
                    if (total + bytes > archives.maximumBytes())
                        throw new IllegalArgumentException("EXPORT_SIZE_LIMIT");
                    jdbc.update(
                            "INSERT IGNORE INTO privacy_export_chunk(task_id,domain,dataset,page_offset,payload,sha256,bytes) VALUES(?,?,?,?,?,?,?)",
                            id,
                            domain,
                            dataset,
                            offset,
                            payload,
                            PrivacyDomain.digest(payload),
                            bytes);
                    jdbc.update(
                            "UPDATE privacy_task_domain SET dataset_index=?,page_offset=? WHERE task_id=? AND domain=?",
                            page.nextOffset() == null ? index + 1 : index,
                            page.nextOffset() == null ? 0 : page.nextOffset(),
                            id,
                            domain);
                });
    }

    private void saveDomain(String id, String domain, String token, String status, String result) {
        tx.executeWithoutResult(
                s -> {
                    lock(id, token);
                    // Cleanup inventory is the approved impact snapshot; execution must not
                    // overwrite it.
                    if ("PLANNED".equals(status))
                        jdbc.update(
                                "UPDATE privacy_task_domain SET status=?,inventory=?,result=? WHERE task_id=? AND domain=?",
                                status,
                                result,
                                result,
                                id,
                                domain);
                    else
                        jdbc.update(
                                "UPDATE privacy_task_domain SET status=?,result=? WHERE task_id=? AND domain=?",
                                status,
                                result,
                                id,
                                domain);
                });
    }

    private void lock(String id, String token) {
        var rows =
                jdbc.queryForList(
                        "SELECT id FROM privacy_task WHERE id=? AND lease_token=? AND lease_until>NOW(3) FOR UPDATE",
                        id,
                        token);
        if (rows.isEmpty()) throw new ConflictException("隐私任务租约已变化");
    }

    private void finish(Map<String, Object> task, String token) throws IOException {
        String id = task.get("id").toString();
        if ("CLEANUP".equals(task.get("kind"))) {
            var results =
                    jdbc.queryForList(
                            "SELECT domain,result FROM privacy_task_domain WHERE task_id=? ORDER BY domain",
                            id);
            String hash = PrivacyDomain.digest(json.writeValueAsString(results));
            jdbc.update(
                    "UPDATE privacy_task SET status=?,plan_hash=IF(approved_by IS NULL,?,plan_hash),completed_at=IF(approved_by IS NULL,NULL,NOW(3)) WHERE id=? AND lease_token=?",
                    task.get("approved_by") == null ? "APPROVAL_REQUIRED" : "COMPLETED",
                    hash,
                    id,
                    token);
            return;
        }
        Path file = archives.workingFile(id);
        var heartbeat =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            var thread = new Thread(r, "privacy-archive-lease");
                            thread.setDaemon(true);
                            return thread;
                        });
        heartbeat.scheduleAtFixedRate(
                () -> {
                    jdbc.update(
                            "UPDATE privacy_task SET lease_until=NOW(3)+INTERVAL 60 SECOND WHERE id=? AND lease_token=? AND status='PENDING' AND lease_until>NOW(3)",
                            id,
                            token);
                },
                10,
                10,
                java.util.concurrent.TimeUnit.SECONDS);
        try {
            List<Map<String, Object>> manifest = new ArrayList<>();
            try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
                jdbc.query(
                        "SELECT domain,dataset,page_offset,payload,sha256 FROM privacy_export_chunk WHERE task_id=? ORDER BY domain,dataset,page_offset",
                        rs -> {
                            String name =
                                    rs.getString("domain")
                                            + "/"
                                            + rs.getString("dataset")
                                            + "-"
                                            + rs.getInt("page_offset")
                                            + ".json";
                            try {
                                zip.putNextEntry(new ZipEntry(name));
                                zip.write(
                                        rs.getString("payload")
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                zip.closeEntry();
                                manifest.add(
                                        Map.of("path", name, "sha256", rs.getString("sha256")));
                            } catch (IOException failure) {
                                throw new java.io.UncheckedIOException(failure);
                            }
                        },
                        id);
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(
                        json.writeValueAsString(
                                        Map.of(
                                                "schema",
                                                "tianji-privacy-export-v1",
                                                "taskId",
                                                id,
                                                "userId",
                                                task.get("user_id"),
                                                "createdAt",
                                                Instant.now().toString(),
                                                "consistency",
                                                "PER_DOMAIN_PAGED_OBSERVATION",
                                                "files",
                                                manifest,
                                                "domains",
                                                jdbc.queryForList(
                                                        "SELECT domain,result FROM privacy_task_domain WHERE task_id=? ORDER BY domain",
                                                        id)))
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            long size = Files.size(file);
            String hash;
            try (var input = Files.newInputStream(file)) {
                var digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[65536];
                int n;
                while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n);
                hash = HexFormat.of().formatHex(digest.digest());
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
            archives.save(id, hash, file);
            tx.executeWithoutResult(
                    s -> {
                        lock(id, token);
                        jdbc.update(
                                "UPDATE privacy_task SET status='READY',artifact_sha256=?,artifact_size=?,completed_at=NOW(3),expires_at=NOW(3)+INTERVAL 24 HOUR WHERE id=?",
                                hash,
                                size,
                                id);
                    });
        } finally {
            heartbeat.shutdownNow();
            // Only the task-owned temporary ZIP, never source media or operator files.
            Files.deleteIfExists(file);
        }
    }
}
