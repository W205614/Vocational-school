package com.zhixuetang.api.client.media;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;
import java.util.Set;

@FeignClient("media-service")
public interface MediaClient {
    /** Registered server-side duration, in milliseconds. Private service endpoint only. */
    @GetMapping("/internal/v2/media/{id}/duration")
    Long durationMillis(@PathVariable("id") Long mediaId);

    @PostMapping("/internal/v2/media/durations")
    Map<Long, Long> readyDurations(@RequestBody Set<Long> mediaIds);
}
