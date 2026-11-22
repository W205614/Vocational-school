package com.zhixuetang.course.controller;

import com.zhixuetang.api.dto.course.SectionInfoDTO;
import com.zhixuetang.common.exceptions.ForbiddenException;
import com.zhixuetang.common.utils.InternalAuth;
import com.zhixuetang.course.constants.CourseConstants.CataType;
import com.zhixuetang.course.properties.CourseProperties;
import com.zhixuetang.course.service.ICourseCatalogueDraftService;
import com.zhixuetang.course.service.ICourseDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class InternalTrialSectionController {
    private final ICourseCatalogueDraftService catalogue;
    private final ICourseDraftService drafts;
    private final CourseProperties properties;

    @GetMapping("/internal/v1/course/{courseId}/trial-sections/{sectionId}")
    @Transactional(readOnly = true)
    public SectionInfoDTO draftTrialSection(@PathVariable long courseId, @PathVariable long sectionId) {
        InternalAuth.requireService();
        var section = catalogue.getById(sectionId);
        if (section == null
                || !Long.valueOf(courseId).equals(section.getCourseId())
                || !Integer.valueOf(CataType.SECTION).equals(section.getType())
                || !Integer.valueOf(1).equals(section.getTrailer())
                || section.getMediaId() == null
                || section.getMediaId() <= 0
                || drafts.getById(courseId) == null) {
            throw new ForbiddenException("课程草稿的小节不存在或未启用试看");
        }
        return new SectionInfoDTO(
                courseId, section.getMediaId(), true, properties.getMedia().getTrailerDuration(),
                section.getMediaDuration(), section.getType());
    }
}
