package com.zhixuetang.user.privacy;

import com.zhixuetang.common.autoconfigure.privacy.PrivacyDomain;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public final class PrivacyRetention {
    public record Rule(int days, String action) {}

    public record Policy(String version, Map<String, Rule> rules) {}

    private final Path file;
    private final JsonMapper json;

    public PrivacyRetention(Environment env, JsonMapper json) {
        String location = env.getProperty("zxt.privacy.retention-policy-file", "");
        file = location.isBlank() ? null : Path.of(location);
        this.json = json;
    }

    public Policy current() {
        if (file == null) return new Policy("UNCONFIGURED", Map.of());
        try {
            var policy = json.readValue(Files.readString(file), Policy.class);
            if (policy.version() == null
                    || !policy.version().matches("[A-Za-z0-9_.-]{1,64}")
                    || policy.rules() == null) throw new IllegalArgumentException("隐私保留规则缺少版本或规则");
            for (var e : policy.rules().entrySet())
                if (!e.getKey().matches("[a-z]+\\.[a-z_]+")
                        || e.getValue().days() < 0
                        || e.getValue().days() > 36500
                        || !"ANONYMIZE".equals(e.getValue().action()))
                    throw new IllegalArgumentException("隐私保留规则只接受明确天数和 ANONYMIZE");
            return policy;
        } catch (java.io.IOException invalid) {
            throw new IllegalStateException("无法读取隐私保留规则", invalid);
        }
    }

    public String hash(Policy policy) {
        return PrivacyDomain.digest(
                json.writeValueAsString(
                        Map.of(
                                "version",
                                policy.version(),
                                "rules",
                                new TreeMap<>(policy.rules()))));
    }

    public Map<String, Map<String, String>> cutoffs(Policy policy, Instant asOf) {
        Map<String, Map<String, String>> result = new TreeMap<>();
        policy.rules()
                .forEach(
                        (key, rule) -> {
                            String[] names = key.split("\\.", 2);
                            result.computeIfAbsent(names[0], x -> new TreeMap<>())
                                    .put(
                                            names[1],
                                            asOf.minus(Duration.ofDays(rule.days())).toString());
                        });
        return result;
    }
}
