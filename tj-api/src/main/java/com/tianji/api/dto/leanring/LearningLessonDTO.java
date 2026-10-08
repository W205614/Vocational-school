package com.tianji.api.dto.leanring;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.time.LocalDateTime;

@Data
@Schema(description = "学习课表进度信息")
public class LearningLessonDTO {
    @Schema(description = "课表id")
    private Long id;
    @Schema(description = "最近学习的小节id")
    private Long latestSectionId;
    @Schema(description = "当前课程状态，0-未学习，1-学习中，2-已学完，3-权益已失效")
    private Integer status;
    @Schema(description = "当前有效权益期限，永久权益或无有效权益时为空；结合status判断")
    private LocalDateTime expireTime;
    @Schema(description = "已学习章节数，权益失效后仍保留")
    private Integer learnedSections;
    @Schema(description = "学习过的小节的记录")
    private List<LearningRecordDTO> records;
}
