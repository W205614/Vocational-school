package com.zhixuetang.course.domain.vo;

import com.zhixuetang.api.dto.leanring.CourseStudyCounts;

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
        List<Field> missingFields,
        CourseStudyCounts counts) {
    public CourseStudyInformationVO withCounts(CourseStudyCounts counts) {
        return new CourseStudyInformationVO(id, name, status, learningOutcomes, prerequisites, difficulty,
                estimatedStudyMinutes, videoEstimateMinutes, practiceOverview, missingFields, counts);
    }

    public enum Sort { NEWEST, ENROLLED, STARTED }

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
