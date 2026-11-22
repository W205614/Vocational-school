package com.zhixuetang.course.domain.vo;

import com.zhixuetang.api.dto.media.TrialStatus;
import java.time.LocalDateTime;
import java.util.List;

public record CoursePublicationVO(Preview preview, List<Issue> issues, List<Trial> trials) {
    public record Issue(String area, Long sectionId, String message, boolean blocking) {}

    public record Trial(Long sectionId, String sectionName, TrialStatus status) {}

    public record Preview(
            String name,
            String coverUrl,
            Integer price,
            Integer sectionNum,
            LocalDateTime purchaseEndTime,
            String introduce,
            String detail,
            String usePeople,
            String learningOutcomes,
            String prerequisites,
            String difficulty,
            String practiceOverview,
            Integer estimatedStudyMinutes,
            String handout,
            List<CataVO> chapters) {}
}
