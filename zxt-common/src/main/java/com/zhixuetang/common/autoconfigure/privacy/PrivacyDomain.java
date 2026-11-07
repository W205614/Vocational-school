package com.zhixuetang.common.autoconfigure.privacy;

import com.zhixuetang.common.exceptions.BadRequestException;
import com.zhixuetang.common.exceptions.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;

/** Executes only the owning module's reviewed queries, never caller-supplied SQL. */
public final class PrivacyDomain {
    public record Dataset(
            String name, String select, String count, String eligible, String cleanup) {}

    public record Definition(String domain, String note, List<Dataset> datasets) {}

    public record Page(
            String domain, String dataset, List<Map<String, Object>> rows, Integer nextOffset) {}

    public record Cleanup(
            String requestId, long userId, String policyVersion, Map<String, String> cutoffs) {}

    private final Definition definition;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final JsonMapper json;

    public PrivacyDomain(
            Definition definition, JdbcTemplate jdbc, TransactionTemplate tx, JsonMapper json) {
        this.definition = definition;
        this.jdbc = jdbc;
        this.tx = tx;
        this.json = json;
    }

    public Definition inventory() {
        // SQL remains private implementation detail, never leaves the domain.
        return new Definition(
                definition.domain(),
                definition.note(),
                definition.datasets().stream()
                        .map(
                                d ->
                                        new Dataset(
                                                d.name(),
                                                null,
                                                null,
                                                null,
                                                d.cleanup() == null ? null : "ANONYMIZE"))
                        .toList());
    }

    public Page export(long userId, String dataset, int offset) {
        if (userId <= 0 || offset < 0) throw new BadRequestException("导出范围无效");
        Dataset spec = dataset(dataset);
        var rows = jdbc.queryForList(spec.select() + " LIMIT 201 OFFSET ?", userId, offset);
        boolean more = rows.size() > 200;
        if (more) rows = new ArrayList<>(rows.subList(0, 200));
        for (var row : rows)
            row.replaceAll(
                    (key, value) ->
                            value instanceof java.sql.Timestamp time
                                    ? time.toInstant().toString()
                                    : value instanceof java.sql.Date date
                                            ? date.toLocalDate().toString()
                                            : value);
        return new Page(
                definition.domain(), dataset, rows, more ? Math.addExact(offset, 200) : null);
    }

    public Map<String, Object> plan(long userId, Map<String, String> cutoffs) {
        if (userId <= 0) throw new BadRequestException("用户标识无效");
        validateCutoffs(cutoffs);
        var result = new LinkedHashMap<String, Object>();
        for (Dataset spec : definition.datasets()) {
            String cutoff = cutoffs.get(spec.name());
            long total = jdbc.queryForObject(spec.count(), Long.class, userId);
            boolean clean = cutoff != null && spec.cleanup() != null;
            long eligible =
                    clean
                            ? jdbc.queryForObject(
                                    spec.eligible(),
                                    Long.class,
                                    userId,
                                    java.sql.Timestamp.from(Instant.parse(cutoff)))
                            : 0;
            result.put(
                    spec.name(),
                    Map.of(
                            "total",
                            total,
                            "eligible",
                            eligible,
                            "action",
                            clean ? "ANONYMIZE" : "RETAIN",
                            "cutoff",
                            clean ? cutoff : "NOT_CONFIGURED",
                            "reason",
                            clean ? "按已配置保留规则匿名化" : "未配置可执行清理规则或业务事实必须保留"));
        }
        return result;
    }

    public Map<String, Object> cleanup(Cleanup request) {
        if (request.requestId() == null
                || !request.requestId().matches("[0-9a-f-]{36}")
                || request.userId() <= 0
                || request.policyVersion() == null
                || !request.policyVersion().matches("[A-Za-z0-9_.-]{1,64}"))
            throw new BadRequestException("清理申请无效");
        validateCutoffs(request.cutoffs());
        String fingerprint = digest(json.writeValueAsString(new TreeMap<>(request.cutoffs())));
        return tx.execute(
                status -> {
                    jdbc.update(
                            "INSERT IGNORE INTO privacy_domain_execution(request_id,user_id,policy_version,request_hash) VALUES(?,?,?,?)",
                            request.requestId(),
                            request.userId(),
                            request.policyVersion(),
                            fingerprint);
                    var row =
                            jdbc.queryForMap(
                                    "SELECT * FROM privacy_domain_execution WHERE request_id=? FOR UPDATE",
                                    request.requestId());
                    if (((Number) row.get("user_id")).longValue() != request.userId()
                            || !request.policyVersion().equals(row.get("policy_version"))
                            || !fingerprint.equals(row.get("request_hash")))
                        throw new ConflictException("同一清理申请的范围已变化");
                    if (row.get("result") != null)
                        return json.readValue(row.get("result").toString(), Map.class);
                    var result = new LinkedHashMap<String, Object>();
                    for (Dataset spec : definition.datasets()) {
                        String cutoff = request.cutoffs().get(spec.name());
                        int changed =
                                cutoff != null && spec.cleanup() != null
                                        ? jdbc.update(
                                                spec.cleanup(),
                                                request.userId(),
                                                java.sql.Timestamp.from(Instant.parse(cutoff)))
                                        : 0;
                        result.put(
                                spec.name(),
                                Map.of(
                                        "anonymized",
                                        changed,
                                        "retained",
                                        spec.cleanup() == null || cutoff == null));
                    }
                    jdbc.update(
                            "UPDATE privacy_domain_execution SET result=?,completed_at=NOW(3) WHERE request_id=?",
                            json.writeValueAsString(result),
                            request.requestId());
                    return result;
                });
    }

    private Dataset dataset(String name) {
        return definition.datasets().stream()
                .filter(d -> d.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("未知隐私数据集"));
    }

    private void validateCutoffs(Map<String, String> cutoffs) {
        if (cutoffs == null) throw new BadRequestException("缺少清理范围");
        for (var entry : cutoffs.entrySet()) {
            Dataset spec = dataset(entry.getKey());
            if (spec.cleanup() == null) throw new BadRequestException("该数据集仅允许保留");
            try {
                if (Instant.parse(entry.getValue()).isAfter(Instant.now()))
                    throw new IllegalArgumentException();
            } catch (RuntimeException invalid) {
                throw new BadRequestException("清理截止时间无效");
            }
        }
    }

    public static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            value.getBytes(
                                                    java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
