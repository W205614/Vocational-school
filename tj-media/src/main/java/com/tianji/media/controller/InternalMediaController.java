package com.tianji.media.controller;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.InternalAuth;
import com.tianji.media.service.IMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class InternalMediaController {
    private final IMediaService media;

    @GetMapping("/internal/v2/media/{id}/duration")
    public Long durationMillis(@PathVariable Long id) {
        InternalAuth.requireService();
        if(id==null || id<=0)throw new BadRequestException("无效的媒资编号");
        var row=media.getById(id);
        Float duration=row==null?null:row.getDuration();
        if(duration==null || !Float.isFinite(duration) || duration<=0 || duration>86400)
            throw new BadRequestException("媒资不存在或服务端时长无效");
        // Duration was recorded to millisecond precision by the native probe. Round
        // away Float representation noise (e.g. 1.2800000001), rather than ceil to seconds.
        return Math.max(1,Math.round(duration.doubleValue()*1000));
    }
}
