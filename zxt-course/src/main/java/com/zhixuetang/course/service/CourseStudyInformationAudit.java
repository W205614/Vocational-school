package com.zhixuetang.course.service;

import com.zhixuetang.api.client.user.UserClient;
import com.zhixuetang.api.dto.user.UserDTO;
import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.common.domain.query.PageQuery;
import com.zhixuetang.course.domain.vo.CourseStudyInformationVO.Field;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CourseStudyInformationAudit {
    private final JdbcTemplate jdbc;
    private final UserClient users;

    public record Change(Field field, String previousValue, String newValue,
                         long operatorId, String operatorName, LocalDateTime changedAt) {}

    public Map<Field, String> snapshot(long courseId) {
        return jdbc.query("""
                SELECT learning_outcomes, prerequisites, difficulty, estimated_study_minutes, practice_overview
                FROM course_content WHERE id = ? AND deleted = 0
                """, rows -> {
            if (!rows.next()) return Map.of();
            var values = new java.util.EnumMap<Field, String>(Field.class);
            values.put(Field.OUTCOMES, rows.getString("learning_outcomes"));
            values.put(Field.PREREQUISITES, rows.getString("prerequisites"));
            values.put(Field.DIFFICULTY, rows.getString("difficulty"));
            values.put(Field.ESTIMATED_TIME, rows.getString("estimated_study_minutes"));
            values.put(Field.PRACTICE, rows.getString("practice_overview"));
            return values;
        }, courseId);
    }

    public boolean recordChanges(long courseId, long operator, Map<Field, String> previous) {
        var changes = new ArrayList<Object[]>();
        snapshot(courseId).forEach((field, after) -> {
            String before = previous.get(field);
            if (!Objects.equals(before, after)) {
                changes.add(new Object[] {courseId, field.name(), before, after, operator});
            }
        });
        if (changes.isEmpty()) return false;
        jdbc.batchUpdate("""
                INSERT INTO course_study_information_change (course_id, field, previous_value, new_value, operator_id)
                VALUES (?, ?, ?, ?, ?)
                """, changes);
        return true;
    }

    @Transactional(readOnly = true)
    public PageDTO<Change> history(long courseId, PageQuery page) {
        int offset = page.from();
        int size = page.getPageSize();
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM course_study_information_change WHERE course_id = ?", Long.class, courseId);
        var rows = jdbc.query("""
                SELECT field, previous_value, new_value, operator_id, changed_at
                FROM course_study_information_change WHERE course_id = ?
                ORDER BY changed_at DESC, id DESC LIMIT ? OFFSET ?
                """, (row, n) -> new Change(Field.valueOf(row.getString("field")), row.getString("previous_value"),
                row.getString("new_value"), row.getLong("operator_id"), null,
                row.getObject("changed_at", LocalDateTime.class)), courseId, size, offset);
        var names = new HashMap<Long, String>();
        if (!rows.isEmpty()) {
            for (UserDTO user : users.queryUserByIds(rows.stream().map(Change::operatorId).distinct().toList())) {
                names.put(user.getId(), user.getName());
            }
        }
        return new PageDTO<>(total, (total + size - 1) / size,
                rows.stream().map(row -> new Change(row.field(), row.previousValue(), row.newValue(),
                        row.operatorId(), names.get(row.operatorId()), row.changedAt())).toList());
    }
}
