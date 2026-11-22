package com.zhixuetang.course.controller;

import com.zhixuetang.api.dto.course.ExamSectionPublicationDTO;
import com.zhixuetang.common.exceptions.BadRequestException;
import com.zhixuetang.common.utils.InternalAuth;
import com.zhixuetang.course.constants.CourseConstants.CataType;
import com.zhixuetang.course.domain.po.CourseCataSubjectDraft;
import com.zhixuetang.course.mapper.CourseCataSubjectDraftMapper;
import com.zhixuetang.course.service.ICourseCatalogueDraftService;
import com.zhixuetang.course.service.ICourseCatalogueService;
import com.zhixuetang.course.service.ICourseDraftService;
import com.zhixuetang.course.service.ICourseService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/course")
public class InternalExamSectionController {
    private final ICourseDraftService drafts;
    private final ICourseCatalogueDraftService draftCatalogues;
    private final ICourseService courses;
    private final ICourseCatalogueService catalogues;
    private final CourseCataSubjectDraftMapper draftQuestions;

    @GetMapping("/{courseId}/exam-sections/{sectionId}")
    public ExamSectionPublicationDTO examSection(
            @PathVariable long courseId, @PathVariable long sectionId) {
        return publicationSection(courseId, sectionId, CataType.PRATICE);
    }

    @GetMapping("/{courseId}/lecture-sections/{sectionId}")
    public ExamSectionPublicationDTO lectureSection(
            @PathVariable long courseId, @PathVariable long sectionId) {
        return publicationSection(courseId, sectionId, CataType.SECTION);
    }

    private ExamSectionPublicationDTO publicationSection(long courseId, long sectionId, int type) {
        InternalAuth.requireService();
        if (courseId <= 0 || sectionId <= 0)
            throw new BadRequestException("小节不属于该课程");
        if (drafts.getById(courseId) != null) {
            var section = draftCatalogues.getById(sectionId);
            if (section == null
                    || !Objects.equals(section.getCourseId(), courseId)
                    || !Integer.valueOf(type).equals(section.getType()))
                throw new BadRequestException("小节不属于该课程草稿");
            var questionIds = type == CataType.PRATICE
                    ? draftQuestions.getByCourseId(courseId).stream()
                            .filter(link -> Objects.equals(link.getCataId(), sectionId))
                            .map(CourseCataSubjectDraft::getSubjectId)
                            .distinct()
                            .sorted()
                            .toList()
                    : null;
            return new ExamSectionPublicationDTO(
                    draftCatalogues.totalSectionNums(courseId), section.getMediaDuration(), questionIds);
        }
        var section = catalogues.getById(sectionId);
        if (section == null
                || !Objects.equals(section.getCourseId(), courseId)
                || !Integer.valueOf(type).equals(section.getType())
                || Integer.valueOf(1).equals(section.getDeleted()))
            throw new BadRequestException("小节不属于该课程");
        return new ExamSectionPublicationDTO(
                courses.getInfoById(courseId, false, false).getSectionNum(), section.getMediaDuration(), null);
    }
}
