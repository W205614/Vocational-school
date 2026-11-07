package com.tianji.api.client.privacy;

import com.tianji.common.autoconfigure.privacy.PrivacyDomain;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

public interface PrivacyDomainClient {
    String domain();

    @GetMapping("/internal/privacy/inventory")
    PrivacyDomain.Definition inventory();

    @GetMapping("/internal/privacy/export")
    PrivacyDomain.Page export(
            @RequestParam("userId") long user,
            @RequestParam("dataset") String dataset,
            @RequestParam("offset") int offset);

    @PostMapping("/internal/privacy/plan")
    Map<String, Object> plan(
            @RequestParam("userId") long user, @RequestBody Map<String, String> cutoffs);

    @PostMapping("/internal/privacy/cleanup")
    Map<String, Object> cleanup(@RequestBody PrivacyDomain.Cleanup request);
}
