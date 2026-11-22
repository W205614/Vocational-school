package com.zhixuetang.api.dto.course;

public record CourseMediaReference(Long courseId, String courseName, int courseStatus,
                                   Long sectionId, String sectionName, boolean draft) {}
