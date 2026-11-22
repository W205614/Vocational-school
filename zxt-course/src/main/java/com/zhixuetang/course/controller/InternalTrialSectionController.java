package com.zhixuetang.course.controller;

import com.zhixuetang.api.dto.course.SectionInfoDTO;
import com.zhixuetang.api.dto.course.TrialSection;
import com.zhixuetang.common.exceptions.ForbiddenException;
import com.zhixuetang.common.utils.InternalAuth;
import com.zhixuetang.course.constants.CourseConstants.CataType;
import com.zhixuetang.course.properties.CourseProperties;
import com.zhixuetang.course.service.ICourseCatalogueDraftService;
import com.zhixuetang.course.service.ICourseDraftService;
import com.zhixuetang.course.service.ICourseService;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Hidden
public class InternalTrialSectionController {
    private final ICourseCatalogueDraftService catalogue;
    private final ICourseDraftService drafts;
    private final CourseProperties properties;
    private final ICourseService courses;
    private final JdbcTemplate jdbc;

    @GetMapping("/internal/v1/course/{courseId}/trial-sections")
    @Transactional(readOnly = true)
    public List<TrialSection> trialSections(@PathVariable long courseId, @RequestParam boolean draft) {
        InternalAuth.requireService();
        if (draft) {
            if (drafts.getById(courseId) == null) throw new ForbiddenException("课程草稿不存在");
        } else {
            var course = courses.getById(courseId);
            if (course == null || !Set.of(2, 4).contains(course.getStatus()))
                throw new ForbiddenException("仅已上架或已完结课程可准备公开试看");
        }
        String table = draft ? "course_catalogue_draft" : "course_catalogue";
        int seconds = properties.getMedia().getTrailerDuration() * 60;
        return jdbc.query("""
                SELECT s.id,s.name,s.media_id
                FROM %1$s s
                JOIN %1$s chapter ON chapter.id=s.parent_catalogue_id AND chapter.course_id=s.course_id
                    AND chapter.type=1 AND chapter.deleted=0
                WHERE s.course_id=? AND s.type=2 AND s.trailer=1 AND s.deleted=0
                ORDER BY chapter.c_index,s.c_index,s.id
                """.formatted(table), (rs, row) -> new TrialSection(rs.getLong("id"), rs.getString("name"),
                        rs.getLong("media_id"), seconds), courseId);
    }

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
