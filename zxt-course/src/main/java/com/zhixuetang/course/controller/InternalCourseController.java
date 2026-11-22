package com.zhixuetang.course.controller;

import com.zhixuetang.course.domain.dto.CourseSimpleInfoListDTO;
import com.zhixuetang.course.service.ICourseService;
import com.zhixuetang.common.utils.InternalAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Hidden
@RequestMapping("/internal/v1/courses")
public class InternalCourseController {
    private final ICourseService courses;
    private final JdbcTemplate jdbc;

    @GetMapping("/taught")
    public List<Long> taughtCourses(@RequestParam long teacherId) {
        InternalAuth.requireService();
        return jdbc.queryForList(
                "SELECT DISTINCT t.course_id FROM course_teacher t JOIN course c ON c.id=t.course_id AND c.deleted=0 WHERE t.teacher_id=? AND t.deleted=0 ORDER BY t.course_id",
                Long.class,
                teacherId);
    }

    @GetMapping("/simpleInfo/list")
    public Object simple(CourseSimpleInfoListDTO request) {
        InternalAuth.requireService();
        if (request.getIds() == null || request.getIds().isEmpty() || request.getIds().size() > 100)
            throw new com.zhixuetang.common.exceptions.BadRequestException("每次查询 1 到 100 门课程");
        return courses.getSimpleInfoList(request);
    }
}
