package com.zhixuetang.course.controller;

import com.zhixuetang.course.domain.dto.CourseSimpleInfoListDTO;
import com.zhixuetang.course.service.ICourseService;
import com.zhixuetang.common.utils.InternalAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Hidden;

@RestController
@RequiredArgsConstructor
@Hidden
@RequestMapping("/internal/v1/courses")
public class InternalCourseController {
    private final ICourseService courses;

    @GetMapping("/simpleInfo/list")
    public Object simple(CourseSimpleInfoListDTO request) {
        InternalAuth.requireService();
        if (request.getIds() == null || request.getIds().isEmpty() || request.getIds().size() > 100)
            throw new com.zhixuetang.common.exceptions.BadRequestException("每次查询 1 到 100 门课程");
        return courses.getSimpleInfoList(request);
    }
}
