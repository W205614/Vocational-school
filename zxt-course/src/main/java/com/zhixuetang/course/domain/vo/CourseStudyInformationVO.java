package com.zhixuetang.course.domain.vo;

import java.util.List;

public record CourseStudyInformationVO(
        long id,
        String name,
        int status,
        String learningOutcomes,
        String prerequisites,
        String difficulty,
        Integer estimatedStudyMinutes,
        Long videoEstimateMinutes,
        String practiceOverview,
        List<Field> missingFields) {
    public enum Field {
        ALL,
        ANY,
        OUTCOMES,
        PREREQUISITES,
        DIFFICULTY,
        ESTIMATED_TIME,
        PRACTICE
    }
}
