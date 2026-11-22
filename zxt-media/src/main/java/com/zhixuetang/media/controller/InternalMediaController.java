package com.zhixuetang.media.controller;

import com.zhixuetang.common.exceptions.BadRequestException;
import com.zhixuetang.common.utils.InternalAuth;
import com.zhixuetang.media.enums.FileStatus;
import com.zhixuetang.media.service.IMediaService;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class InternalMediaController {
    private final IMediaService media;

    @GetMapping("/internal/v2/media/{id}/duration")
    public Long durationMillis(@PathVariable Long id) {
        InternalAuth.requireService();
        if (id == null || id <= 0) throw new BadRequestException("无效的媒资编号");
        var row = media.getById(id);
        Float duration = row == null ? null : row.getDuration();
        if (!validDuration(duration))
            throw new BadRequestException("媒资不存在或服务端时长无效");
        // Duration was recorded to millisecond precision by the native probe. Round
        // away Float representation noise (e.g. 1.2800000001), rather than ceil to seconds.
        return toMillis(duration);
    }

    @PostMapping("/internal/v2/media/durations")
    public Map<Long, Long> readyDurations(@RequestBody Set<Long> ids) {
        InternalAuth.requireService();
        if (ids == null || ids.isEmpty() || ids.size() > 500
                || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new BadRequestException("媒资编号需为1至500个有效编号");
        Map<Long, Long> durations = new HashMap<>();
        for (var row : media.listByIds(ids)) {
            if ((row.getStatus() == FileStatus.UPLOADED || row.getStatus() == FileStatus.PROCESSED)
                    && !Integer.valueOf(1).equals(row.getDeleted())
                    && validDuration(row.getDuration())) {
                durations.put(row.getId(), toMillis(row.getDuration()));
            }
        }
        return durations;
    }

    private static boolean validDuration(Float duration) {
        return duration != null && Float.isFinite(duration) && duration > 0 && duration <= 86400;
    }

    private static long toMillis(Float duration) {
        return Math.max(1, Math.round(duration.doubleValue() * 1000));
    }
}
