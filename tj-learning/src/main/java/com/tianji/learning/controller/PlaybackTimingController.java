package com.tianji.learning.controller;

import com.tianji.learning.service.impl.PlaybackTimingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/learning-records/playback-sessions")
@RequiredArgsConstructor
public class PlaybackTimingController {
    private final PlaybackTimingService timing;
    @PostMapping public PlaybackTimingService.View start(@RequestBody PlaybackTimingService.Start request){return timing.start(request);}
    @PostMapping("/heartbeat") public PlaybackTimingService.View heartbeat(@RequestBody PlaybackTimingService.Heartbeat request){return timing.heartbeat(request,false);}
    @PostMapping("/end") public PlaybackTimingService.View end(@RequestBody PlaybackTimingService.Heartbeat request){return timing.heartbeat(request,true);}
}
