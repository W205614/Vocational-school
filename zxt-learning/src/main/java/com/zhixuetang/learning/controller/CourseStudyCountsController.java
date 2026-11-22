package com.zhixuetang.learning.controller;

import com.zhixuetang.api.dto.leanring.CourseStudyCounts;
import com.zhixuetang.common.exceptions.BadRequestException;
import com.zhixuetang.common.utils.InternalAuth;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Hidden
@RestController
@RequiredArgsConstructor
public class CourseStudyCountsController {
    private final NamedParameterJdbcTemplate jdbc;

    @PostMapping("/internal/v1/learning/course-study-counts")
    public Map<Long, CourseStudyCounts> byCourse(@RequestBody Set<Long> courseIds) {
        InternalAuth.requireService();
        if (courseIds.isEmpty() || courseIds.size() > 250 || courseIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BadRequestException("每次查询 1 到 250 个有效课程编号");
        }
        return jdbc.query("""
                SELECT l.course_id, COUNT(*) enrolled,
                       SUM(l.latest_learn_time IS NOT NULL OR l.learned_sections > 0) started
                FROM learning_lesson l
                WHERE l.course_id IN (:courseIds) AND EXISTS (
                    SELECT 1 FROM learning_entitlement e WHERE e.course_id = l.course_id AND e.user_id = l.user_id
                    AND e.active = 1 AND (e.expires_at IS NULL OR e.expires_at > CURRENT_TIMESTAMP(3))
                )
                GROUP BY l.course_id
                """, Map.of("courseIds", courseIds), rows -> {
            var result = new HashMap<Long, CourseStudyCounts>();
            while (rows.next()) result.put(rows.getLong("course_id"),
                    new CourseStudyCounts(rows.getLong("enrolled"), rows.getLong("started")));
            return result;
        });
    }
}
