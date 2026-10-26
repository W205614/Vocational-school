package com.tianji.gateway.config;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.core.io.ClassPathResource;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Version-controlled method/path inventory. Unknown endpoints always fail closed. */
@Component
public final class AccessPolicy {
    private final List<String> rules;
    private final AntPathMatcher matcher=new AntPathMatcher();
    public AccessPolicy() {
        try(var input=new ClassPathResource("access-policy.txt").getInputStream()) {
            rules=new String(input.readAllBytes(),StandardCharsets.UTF_8).lines().filter(line->!line.isBlank()).toList();
        } catch(Exception error) {throw new IllegalStateException("Access policy is required",error);}
    }
    public boolean declares(String method,String path) {
        String key=method+":"+path;
        return rules.stream().anyMatch(pattern->matcher.match(pattern,key) &&
            matcher.extractUriTemplateVariables(pattern,key).entrySet().stream().allMatch(variable->valid(pattern,variable.getKey(),variable.getValue())));
    }
    private boolean valid(String pattern,String name,String value) {
        if(name.equals("key"))return value.matches("[a-zA-Z0-9_.-]{1,100}");
        if(name.equals("reason") && pattern.equals("PUT:/api/v2/admin/provider-payments/anomalies/{number}/{reason}"))
            return Set.of("WINDOW_EXHAUSTED","CHANNEL_UNAVAILABLE","ORDER_NUMBER_MISMATCH","AMOUNT_MISMATCH","INVALID_SUCCESS_TIME","UNKNOWN_STATUS").contains(value);
        if(name.equals("month"))return value.matches("[0-9]{4}-[0-9]{2}");
        if(name.equals("kind"))return Set.of("refund","payment","sms","notice").contains(value);
        if(name.equals("code"))return value.matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8,10}");
        if(name.equals("hidden"))return value.matches("true|false");
        if(pattern.contains("/cloud-content/") || pattern.contains("/media-upload-tasks/") || pattern.contains("/account-privacy/requests/"))
            return value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        boolean workflow=pattern.contains("/learning-hub/admin/rewards/") || pattern.contains("/operations/") ||
            pattern.contains("/events/") || pattern.contains("-failures/") || pattern.contains("/storage-cleanups/") ||
            pattern.contains("/accounts/sessions/") || pattern.contains("/deliveries/");
        return value.matches(workflow?"[0-9a-f-]{1,36}":"[0-9]{1,20}");
    }
}
