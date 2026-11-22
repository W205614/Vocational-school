package com.zhixuetang.media.trial;

import com.zhixuetang.api.client.course.CourseClient;
import com.zhixuetang.api.dto.course.TrialSection;
import com.zhixuetang.api.dto.media.TrialStatus;
import com.zhixuetang.common.exceptions.BadRequestException;
import com.zhixuetang.common.utils.UserContext;
import com.zhixuetang.media.cloud.TrialMediaJobs;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/trial-batches")
public class TrialBatchController {
    private final CourseClient courses;
    private final ObjectProvider<TrialMediaJobs> jobs;

    public record TrialBatchItem(Long sectionId, String sectionName, TrialStatus status) {}

    public record TrialBatchRequest(long courseId, boolean draft, Set<Long> sectionIds) {}

    @GetMapping
    public List<TrialBatchItem> statuses(@RequestParam long courseId, @RequestParam boolean draft) {
        UserContext.requireAdmin();
        var sections = sections(courseId, draft);
        return items(sections, statuses(sections, jobs.getIfAvailable()));
    }

    @PostMapping
    public List<TrialBatchItem> prepare(@RequestBody TrialBatchRequest request) {
        UserContext.requireAdmin();
        var sections = sections(request.courseId(), request.draft());
        var ids = request.sectionIds();
        if (ids != null && !ids.isEmpty()) {
            sections = sections.stream().filter(section -> ids.contains(section.sectionId())).toList();
            if (sections.size() != ids.size()) throw new BadRequestException("所选小节不存在或未启用试看，请刷新列表");
        }
        var worker = jobs.getIfAvailable();
        var current = statuses(sections, worker);
        if (worker != null) {
            for (var entry : current.entrySet()) {
                var status = entry.getValue();
                if (status.state() == TrialStatus.State.MISSING) {
                    try {
                        worker.request(entry.getKey(), sections.getFirst().seconds());
                    } catch (BadRequestException changed) {
                        // A source removed after the initial read is reported by the final per-section status.
                        if (worker.statuses(Set.of(entry.getKey()), sections.getFirst().seconds())
                                .containsKey(entry.getKey())) throw changed;
                    }
                } else if (status.state() == TrialStatus.State.FAILED) {
                    worker.retry(status.taskId(), status.version());
                }
            }
        }
        return items(sections, statuses(sections, worker));
    }

    private List<TrialSection> sections(long courseId, boolean draft) {
        if (courseId <= 0) throw new BadRequestException("课程编号无效");
        return courses.trialSections(courseId, draft);
    }

    private static List<TrialBatchItem> items(List<TrialSection> sections, Map<Long, TrialStatus> statuses) {
        return sections.stream().map(section -> new TrialBatchItem(section.sectionId(), section.sectionName(),
                statuses.getOrDefault(section.mediaId(), TrialStatus.unavailable()))).toList();
    }

    private static Map<Long, TrialStatus> statuses(List<TrialSection> sections, TrialMediaJobs worker) {
        Map<Long, TrialStatus> result = new HashMap<>();
        if (worker == null || sections.isEmpty()) return result;
        var ids = sections.stream().map(TrialSection::mediaId).filter(id -> id > 0).distinct().toList();
        for (int offset = 0; offset < ids.size(); offset += 500) {
            result.putAll(worker.statuses(Set.copyOf(ids.subList(offset, Math.min(offset + 500, ids.size()))),
                    sections.getFirst().seconds()));
        }
        return result;
    }
}
