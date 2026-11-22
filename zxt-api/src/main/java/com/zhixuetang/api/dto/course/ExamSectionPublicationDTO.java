package com.zhixuetang.api.dto.course;

import java.util.List;

public record ExamSectionPublicationDTO(
        Integer totalSections, Integer mediaDuration, List<Long> draftQuestionIds) {}
