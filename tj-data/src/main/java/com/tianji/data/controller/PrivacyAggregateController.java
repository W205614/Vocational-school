package com.tianji.data.controller;

import com.tianji.common.autoconfigure.privacy.PrivacyDomain;
import com.tianji.common.utils.*;
import com.tianji.common.exceptions.ForbiddenException;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/internal/privacy")
public final class PrivacyAggregateController {
    private void authorize() {
        InternalAuth.requireService();
        if (ServiceIdentity.current().directed()
                && !"identity".equals(WebUtils.getRequest().getHeader("X-TJ-Service")))
            throw new ForbiddenException("仅身份域可协调隐私申请");
    }

    @GetMapping("/inventory")
    public PrivacyDomain.Definition inventory() {
        authorize();
        return new PrivacyDomain.Definition("data", "本模块仅提供按课程汇总的派生统计，不持有个人账户数据。", List.of());
    }

    @PostMapping("/plan")
    public Map<String, Object> plan(
            @RequestParam long userId, @RequestBody Map<String, String> cutoffs) {
        authorize();
        if (!cutoffs.isEmpty())
            throw new com.tianji.common.exceptions.BadRequestException("聚合域没有可清理个人数据集");
        return Map.of("aggregate", "NO_PERSONAL_DATA");
    }

    @GetMapping("/export")
    public PrivacyDomain.Page export(
            @RequestParam long userId, @RequestParam String dataset, @RequestParam int offset) {
        authorize();
        throw new com.tianji.common.exceptions.BadRequestException("聚合域没有个人数据集");
    }

    @PostMapping("/cleanup")
    public Map<String, Object> cleanup(@RequestBody PrivacyDomain.Cleanup request) {
        return plan(request.userId(), request.cutoffs());
    }
}
