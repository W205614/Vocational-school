package com.zhixuetang.course.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

public record CoursePublicationVO(Preview preview, List<Issue> issues) {
    public record Issue(String area, Long sectionId, String message, boolean blocking) {}

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
            String handout,
            List<CataVO> chapters) {}
}
