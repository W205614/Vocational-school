package com.tianji.gateway;

import com.tianji.gateway.config.AccessPolicy;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProductionReadinessAccessPolicyTest {
    private final AccessPolicy policy = new AccessPolicy();
    private final String uuid = "23456789-2345-2345-2345-23456789abcd";

    @Test
    void privacyTaskOperationsRequireCanonicalUuidAcrossBothAudiences() {
        for (String audience : List.of("services", "admin")) {
            String base = "/api/v2/" + audience + "/user/account-privacy/tasks/";
            assertTrue(policy.declares("GET", base + uuid));
            for (String action : List.of("approve", "retry", "download"))
                assertTrue(policy.declares("POST", base + uuid + "/" + action));
            for (String invalid :
                    List.of(
                            "123",
                            "a".repeat(64),
                            uuid.toUpperCase(),
                            uuid.replace("-", ""),
                            "-".repeat(36),
                            uuid + "/extra",
                            "%2e%2e")) {
                assertFalse(policy.declares("GET", base + invalid), invalid);
                assertFalse(policy.declares("POST", base + invalid + "/download"), invalid);
            }
            assertFalse(policy.declares("DELETE", base + uuid));
            assertFalse(policy.declares("GET", base + uuid + "/download"));
        }
    }

    @Test
    void trialStreamsAndAdministrativeRetriesUseTheirExactSha256Shape() {
        for (String audience : List.of("services", "admin")) {
            String base = "/api/v2/" + audience + "/media/";
            assertTrue(policy.declares("GET", base + "trial-content/" + "a".repeat(64)));
            assertTrue(policy.declares("POST", base + "media-trials/" + "a".repeat(64) + "/retry"));
            for (String invalid :
                    List.of(
                            uuid,
                            "123",
                            "a".repeat(63),
                            "a".repeat(65),
                            "A".repeat(64),
                            "g".repeat(64),
                            "%2e%2e",
                            "a".repeat(64) + "/extra")) {
                assertFalse(policy.declares("GET", base + "trial-content/" + invalid), invalid);
                assertFalse(
                        policy.declares("POST", base + "media-trials/" + invalid + "/retry"),
                        invalid);
            }
            assertFalse(policy.declares("POST", base + "trial-content/" + "a".repeat(64)));
            assertFalse(policy.declares("GET", base + "media-trials/" + "a".repeat(64) + "/retry"));
        }
    }
}
