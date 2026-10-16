package com.tianji.api.client.message;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(value="message-service",contextId="learningNotificationClient")
public interface LearningNotificationClient {
    @GetMapping("/internal/v1/inboxes/unread-interactions")
    Integer unreadInteractions();
}
