package com.zhixuetang.course.controller;

import com.zhixuetang.api.dto.course.CourseMediaReference;
import com.zhixuetang.common.exceptions.BadRequestException;
import com.zhixuetang.common.utils.InternalAuth;
import com.zhixuetang.course.service.CourseMediaDeletionGuard;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/course/media")
public class InternalCourseMediaController {
    private final CourseMediaDeletionGuard guard;

    @GetMapping("/{mediaId}/references")
    public List<CourseMediaReference> references(@PathVariable long mediaId) {
        InternalAuth.requireService();
        if (mediaId <= 0) throw new BadRequestException("媒资编号无效");
        return guard.references(mediaId);
    }

    @PostMapping("/deletion-guard")
    public boolean reserveDeletion(@RequestBody List<Long> ids) {
        InternalAuth.requireService();
        if (ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BadRequestException("每次核对 1 到 100 个有效媒资编号");
        }
        return guard.reserveDeletion(ids);
    }
}
