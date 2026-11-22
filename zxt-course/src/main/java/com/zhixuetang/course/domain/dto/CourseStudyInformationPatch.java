package com.zhixuetang.course.domain.dto;

import com.zhixuetang.course.domain.vo.CourseStudyInformationVO.Field;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CourseStudyInformationPatch(
        @NotNull Integer status,
        @NotNull @Size(min = 1, max = 5)
                @Schema(description = "每个待修改字段打开表单时的公开值，未填写为 null")
                Map<Field, @NotNull @Valid ExpectedValue> expectedValues,
        @Size(max = 500, message = "学习成果最多500字") String learningOutcomes,
        @Size(max = 500, message = "先修要求最多500字") String prerequisites,
        @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED", message = "请选择有效的课程难度")
                String difficulty,
        @Min(value = 1, message = "预计学习时长至少1分钟")
                @Max(value = 100000, message = "预计学习时长最多100000分钟") Integer estimatedStudyMinutes,
        @Size(max = 500, message = "实操说明最多500字") String practiceOverview) {
    public record ExpectedValue(@Schema(nullable = true) @Size(max = 500) String value) {}
}
