package com.tianji.common.autoconfigure.privacy;

import com.tianji.common.utils.InternalAuth;
import com.tianji.common.utils.ServiceIdentity;
import com.tianji.common.utils.WebUtils;
import com.tianji.common.exceptions.ForbiddenException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/internal/privacy")
public final class PrivacyDomainController {
    private final PrivacyDomain domain;

    public PrivacyDomainController(PrivacyDomain domain) {
        this.domain = domain;
    }

    private void authorize() {
        InternalAuth.requireService();
        if (ServiceIdentity.current().directed()
                && !"identity".equals(WebUtils.getRequest().getHeader("X-TJ-Service")))
            throw new ForbiddenException("仅身份域可协调隐私申请");
    }

    @GetMapping("/inventory")
    public PrivacyDomain.Definition inventory() {
        authorize();
        return domain.inventory();
    }

    @GetMapping("/export")
    public PrivacyDomain.Page export(
            @RequestParam long userId, @RequestParam String dataset, @RequestParam int offset) {
        authorize();
        return domain.export(userId, dataset, offset);
    }

    @PostMapping("/plan")
    public Map<String, Object> plan(
            @RequestParam long userId, @RequestBody Map<String, String> cutoffs) {
        authorize();
        return domain.plan(userId, cutoffs);
    }

    @PostMapping("/cleanup")
    public Map<String, Object> cleanup(@RequestBody PrivacyDomain.Cleanup request) {
        authorize();
        return domain.cleanup(request);
    }
}
