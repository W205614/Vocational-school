package com.zhixuetang.course.controller;

import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.common.domain.query.PageQuery;
import com.zhixuetang.common.utils.UserContext;
import com.zhixuetang.course.domain.dto.CourseStudyInformationPatch;
import com.zhixuetang.course.domain.vo.CourseStudyInformationVO;
import com.zhixuetang.course.domain.vo.CourseStudyInformationVO.Field;
import com.zhixuetang.course.domain.vo.CourseStudyInformationVO.Sort;
import com.zhixuetang.course.service.CourseStudyInformationAudit;
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
    private final CourseStudyInformationAudit audit;

    @GetMapping
    public PageDTO<CourseStudyInformationVO> inventory(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") @Size(max = 80) String keyword,
            @RequestParam(defaultValue = "ANY") Field missing,
            @RequestParam(defaultValue = "NEWEST") Sort sort) {
        UserContext.requireAdmin();
        return studyInformation.inventory(pageNo, pageSize, keyword.strip(), missing, sort);
    }

    @GetMapping("/{id}")
    public CourseStudyInformationVO detail(@PathVariable @Positive long id) {
        UserContext.requireAdmin();
        return studyInformation.detail(id);
    }

    @GetMapping("/{id}/changes")
    public PageDTO<CourseStudyInformationAudit.Change> changes(@PathVariable @Positive long id, PageQuery page) {
        UserContext.requireAdmin();
        return audit.history(id, page);
    }

    @PatchMapping("/{id}")
    public CourseStudyInformationVO supplement(
            @PathVariable @Positive long id, @RequestBody @Valid CourseStudyInformationPatch patch) {
        UserContext.requireAdmin();
        return studyInformation.supplement(id, patch, UserContext.requireUser());
    }
}
