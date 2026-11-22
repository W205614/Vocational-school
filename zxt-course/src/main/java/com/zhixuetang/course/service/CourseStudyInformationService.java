package com.zhixuetang.course.service;

import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.common.domain.query.PageQuery;
import com.zhixuetang.common.exceptions.BadRequestException;
import com.zhixuetang.common.exceptions.ConflictException;
import com.zhixuetang.course.constants.CourseStatus;
import com.zhixuetang.course.domain.dto.CourseStudyInformationPatch;
import com.zhixuetang.course.domain.vo.CourseStudyInformationVO;
import com.zhixuetang.course.domain.vo.CourseStudyInformationVO.Field;
import com.zhixuetang.course.mapper.CourseDraftMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
public class CourseStudyInformationService {
    private static final String INVENTORY = """
            WITH video_estimates AS (
                SELECT video.course_id, CEIL(SUM(video.media_duration) / 60) AS minutes
                FROM course_catalogue video
                JOIN course_catalogue chapter ON chapter.id = video.parent_catalogue_id
                  AND chapter.course_id = video.course_id
                  AND chapter.parent_catalogue_id = 0 AND chapter.type = 1 AND chapter.deleted = 0
                WHERE video.type = 2 AND video.deleted = 0
                GROUP BY video.course_id
                HAVING MIN(video.media_duration) > 0
            ), inventory AS (
                SELECT c.id, c.name, c.status, info.learning_outcomes, info.prerequisites,
                    info.difficulty, info.estimated_study_minutes, video_estimates.minutes AS video_minutes,
                    info.practice_overview,
                    NOT REGEXP_LIKE(COALESCE(info.learning_outcomes, ''), '[^[:space:]]') AS missing_outcomes,
                    NOT REGEXP_LIKE(COALESCE(info.prerequisites, ''), '[^[:space:]]') AS missing_prerequisites,
                    NOT REGEXP_LIKE(COALESCE(info.difficulty, ''), '^(BEGINNER|INTERMEDIATE|ADVANCED)$', 'c') AS missing_difficulty,
                    (COALESCE(info.estimated_study_minutes, 0) <= 0 AND video_estimates.minutes IS NULL) AS missing_time,
                    NOT REGEXP_LIKE(COALESCE(info.practice_overview, ''), '[^[:space:]]') AS missing_practice
                FROM course c
                LEFT JOIN course_content info ON info.id = c.id AND info.deleted = 0
                LEFT JOIN video_estimates ON video_estimates.course_id = c.id
                WHERE c.deleted = 0 AND c.status IN (2, 4)
            )
            """;
    private static final RowMapper<CourseStudyInformationVO> ROW = (row, rowNumber) -> {
        var missing = new ArrayList<Field>();
        if (row.getBoolean("missing_outcomes")) missing.add(Field.OUTCOMES);
        if (row.getBoolean("missing_prerequisites")) missing.add(Field.PREREQUISITES);
        if (row.getBoolean("missing_difficulty")) missing.add(Field.DIFFICULTY);
        if (row.getBoolean("missing_time")) missing.add(Field.ESTIMATED_TIME);
        if (row.getBoolean("missing_practice")) missing.add(Field.PRACTICE);
        return new CourseStudyInformationVO(
                row.getLong("id"), row.getString("name"), row.getInt("status"),
                row.getString("learning_outcomes"), row.getString("prerequisites"),
                row.getString("difficulty"), row.getObject("estimated_study_minutes", Integer.class),
                row.getObject("video_minutes", Long.class), row.getString("practice_overview"), missing);
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final CourseDraftMapper drafts;

    public CourseStudyInformationService(JdbcTemplate jdbc, CourseDraftMapper drafts) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
        this.drafts = drafts;
    }

    @Transactional(readOnly = true)
    public PageDTO<CourseStudyInformationVO> inventory(
            int pageNo, int pageSize, String keyword, Field missing) {
        int offset = new PageQuery().setPageNo(pageNo).setPageSize(pageSize).from();
        var params = new MapSqlParameterSource()
                .addValue("keyword", keyword)
                .addValue("size", pageSize)
                .addValue("offset", offset);
        String missingCondition = switch (missing) {
            case ALL -> "TRUE";
            case ANY -> "missing_outcomes OR missing_prerequisites OR missing_difficulty OR missing_time OR missing_practice";
            case OUTCOMES -> "missing_outcomes";
            case PREREQUISITES -> "missing_prerequisites";
            case DIFFICULTY -> "missing_difficulty";
            case ESTIMATED_TIME -> "missing_time";
            case PRACTICE -> "missing_practice";
        };
        String from = " FROM inventory WHERE LOCATE(:keyword, name) > 0 AND (" + missingCondition + ")";
        long total = jdbc.queryForObject(INVENTORY + "SELECT COUNT(*)" + from, params, Long.class);
        var list = jdbc.query(
                INVENTORY + "SELECT *" + from + " ORDER BY id DESC LIMIT :size OFFSET :offset", params, ROW);
        return new PageDTO<>(total, (total + pageSize - 1) / pageSize, list);
    }

    public CourseStudyInformationVO detail(long id) {
        return jdbc.query(INVENTORY + "SELECT * FROM inventory WHERE id = :id",
                        new MapSqlParameterSource("id", id), ROW)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BadRequestException("课程当前没有可补录的公开资料"));
    }

    @Transactional
    public CourseStudyInformationVO supplement(long id, CourseStudyInformationPatch patch, long userId) {
        if (!CourseStatus.SHELF.equals(patch.status()) && !CourseStatus.FINISHED.equals(patch.status())) {
            throw new BadRequestException("仅支持补录已上架或已完结的课程");
        }
        String outcomes = provided(patch.learningOutcomes());
        String prerequisites = provided(patch.prerequisites());
        String practice = provided(patch.practiceOverview());
        if (outcomes == null && prerequisites == null && practice == null
                && patch.difficulty() == null && patch.estimatedStudyMinutes() == null) {
            throw new BadRequestException("请填写至少一项选课资料");
        }
        var params = new MapSqlParameterSource("id", id);
        drafts.lockCourseState(id);
        var courses = jdbc.query(
                "SELECT status FROM course WHERE id = :id AND deleted = 0", params,
                (row, rowNumber) -> row.getInt("status"));
        if (courses.isEmpty() || !patch.status().equals(courses.getFirst())) {
            throw new ConflictException("课程状态已变化，请刷新盘点列表后重试");
        }
        var content = jdbc.query("SELECT deleted FROM course_content WHERE id = :id", params,
                (row, rowNumber) -> row.getBoolean("deleted"));
        if (!content.isEmpty() && content.getFirst()) {
            throw new ConflictException("公开课程内容已删除，请核对课程状态");
        }
        params.addValue("userId", userId)
                .addValue("outcomes", outcomes)
                .addValue("prerequisites", prerequisites)
                .addValue("practice", practice)
                .addValue("difficulty", patch.difficulty())
                .addValue("minutes", patch.estimatedStudyMinutes());
        // Historical drafts can coexist with a published course. Propagate only fields
        // that still have their published value so independently edited drafts win on republication.
        jdbc.update("""
                UPDATE course_content_draft draft
                LEFT JOIN course_content published ON published.id = draft.id
                SET draft.learning_outcomes = IF(
                        CAST(draft.learning_outcomes AS BINARY) <=> CAST(published.learning_outcomes AS BINARY),
                        COALESCE(:outcomes, draft.learning_outcomes), draft.learning_outcomes),
                    draft.prerequisites = IF(
                        CAST(draft.prerequisites AS BINARY) <=> CAST(published.prerequisites AS BINARY),
                        COALESCE(:prerequisites, draft.prerequisites), draft.prerequisites),
                    draft.difficulty = IF(
                        CAST(draft.difficulty AS BINARY) <=> CAST(published.difficulty AS BINARY),
                        COALESCE(:difficulty, draft.difficulty), draft.difficulty),
                    draft.estimated_study_minutes = IF(
                        draft.estimated_study_minutes <=> published.estimated_study_minutes,
                        COALESCE(:minutes, draft.estimated_study_minutes), draft.estimated_study_minutes),
                    draft.practice_overview = IF(
                        CAST(draft.practice_overview AS BINARY) <=> CAST(published.practice_overview AS BINARY),
                        COALESCE(:practice, draft.practice_overview), draft.practice_overview)
                WHERE draft.id = :id AND draft.deleted = 0
                """, params);
        jdbc.update("""
                INSERT INTO course_content (
                    id, course_introduce, use_people, course_detail, creater, updater,
                    learning_outcomes, prerequisites, difficulty, estimated_study_minutes, practice_overview
                ) VALUES (
                    :id, '', '', '', :userId, :userId, :outcomes, :prerequisites, :difficulty, :minutes, :practice
                )
                ON DUPLICATE KEY UPDATE
                    learning_outcomes = COALESCE(:outcomes, learning_outcomes),
                    prerequisites = COALESCE(:prerequisites, prerequisites),
                    difficulty = COALESCE(:difficulty, difficulty),
                    estimated_study_minutes = COALESCE(:minutes, estimated_study_minutes),
                    practice_overview = COALESCE(:practice, practice_overview),
                    updater = :userId
                """, params);
        return detail(id);
    }

    private static String provided(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
