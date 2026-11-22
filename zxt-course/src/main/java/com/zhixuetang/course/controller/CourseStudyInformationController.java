package com.zhixuetang.course.controller;

import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.common.utils.UserContext;
import com.zhixuetang.course.domain.dto.CourseStudyInformationPatch;
import com.zhixuetang.course.domain.vo.CourseStudyInformationVO;
import com.zhixuetang.course.domain.vo.CourseStudyInformationVO.Field;
import com.zhixuetang.course.service.CourseStudyInformationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/study-information")
public class CourseStudyInformationController {
    private final CourseStudyInformationService studyInformation;

    @GetMapping
    public PageDTO<CourseStudyInformationVO> inventory(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") @Size(max = 80) String keyword,
            @RequestParam(defaultValue = "ANY") Field missing) {
        UserContext.requireAdmin();
        return studyInformation.inventory(pageNo, pageSize, keyword.strip(), missing);
    }

    @GetMapping("/{id}")
    public CourseStudyInformationVO detail(@PathVariable @Positive long id) {
        UserContext.requireAdmin();
        return studyInformation.detail(id);
    }

    @PatchMapping("/{id}")
    public CourseStudyInformationVO supplement(
            @PathVariable @Positive long id, @RequestBody @Valid CourseStudyInformationPatch patch) {
        UserContext.requireAdmin();
        return studyInformation.supplement(id, patch, UserContext.requireUser());
    }
}
