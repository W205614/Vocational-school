package com.zhixuetang.media.trial;

import com.zhixuetang.api.dto.media.TrialStatus;
import com.zhixuetang.api.dto.media.TrialStatusQuery;
import com.zhixuetang.common.exceptions.BadRequestException;
import com.zhixuetang.common.utils.InternalAuth;
import com.zhixuetang.media.cloud.TrialMediaJobs;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class InternalTrialStatusController {
    private final ObjectProvider<TrialMediaJobs> jobs;

    @PostMapping("/internal/v2/media/trials/status")
    public Map<Long, TrialStatus> statuses(@RequestBody TrialStatusQuery query) {
        InternalAuth.requireService();
        var ids = query.mediaIds();
        if (ids == null || ids.isEmpty() || ids.size() > 500
                || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BadRequestException("媒资编号需为1至500个有效编号");
        }
        if (query.seconds() < 1 || query.seconds() > 1800) {
            throw new BadRequestException("试看时长无效");
        }
        var service = jobs.getIfAvailable();
        return service == null ? Map.of() : service.statuses(ids, query.seconds());
    }
}
