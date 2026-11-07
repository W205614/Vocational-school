package com.zhixuetang.remark.task;

import com.zhixuetang.remark.config.LikedTaskProperties;
import com.zhixuetang.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikedTimesCheckTask {

    private final LikedTaskProperties likedTaskProperties;
    private final ILikedRecordService recordService;

    @Scheduled(fixedDelay = 10 * 1000)
    public void checkLikedTimes() {
        for (String bizType : likedTaskProperties.getBizTypes()) {
            recordService.readLikedTimesAndSendMessage(
                    bizType, likedTaskProperties.getMaxSendSize());
        }
    }
}
