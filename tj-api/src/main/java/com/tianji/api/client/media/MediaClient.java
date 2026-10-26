package com.tianji.api.client.media;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient("media-service")
public interface MediaClient {
    /** Registered server-side duration, in milliseconds. Private service endpoint only. */
    @GetMapping("/internal/v2/media/{id}/duration")
    Long durationMillis(@PathVariable("id") Long mediaId);
}
