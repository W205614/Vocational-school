package com.zhixuetang.course.service;

import com.zhixuetang.api.dto.course.CourseMediaReference;
import com.zhixuetang.common.exceptions.ConflictException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseMediaDeletionGuard {
    private static final String REFERENCES = """
            SELECT c.id course_id,c.name course_name,c.status,s.id section_id,s.name section_name,FALSE draft
            FROM course_catalogue s JOIN course c ON c.id=s.course_id AND c.deleted=0
            WHERE s.media_id IN (:ids) AND s.type=2 AND s.deleted=0
            UNION ALL
            SELECT c.id,c.name,c.status,s.id,s.name,TRUE
            FROM course_catalogue_draft s JOIN course_draft c ON c.id=s.course_id AND c.deleted=0
            WHERE s.media_id IN (:ids) AND s.type=2 AND s.deleted=0
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public CourseMediaDeletionGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    public List<CourseMediaReference> references(long mediaId) {
        return named.query(REFERENCES + " ORDER BY draft,course_id,section_id", Map.of("ids", List.of(mediaId)),
                (rs, row) -> new CourseMediaReference(rs.getLong("course_id"), rs.getString("course_name"),
                        rs.getInt("status"), rs.getLong("section_id"), rs.getString("section_name"),
                        rs.getBoolean("draft")));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean reserveDeletion(List<Long> mediaIds) {
        var ids = lock(mediaIds);
        // READ COMMITTED observes a reference committed while this request waited for its guard row.
        boolean referenced = Boolean.TRUE.equals(named.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM (" + REFERENCES + ") refs)", Map.of("ids", ids), Boolean.class));
        if (referenced) return false;
        named.update("UPDATE course_media_deletion_guard SET deletion_reserved=TRUE WHERE media_id IN (:ids)",
                Map.of("ids", ids));
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireDraftCanPublish(long courseId) {
        requireCanReference(jdbc.queryForList("""
                SELECT media_id FROM course_catalogue_draft
                WHERE course_id=? AND deleted=0 AND type=2 AND media_id>0
                """, Long.class, courseId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireCanReference(Collection<Long> mediaIds) {
        if (mediaIds.isEmpty()) return;
        var ids = lock(mediaIds);
        var reserved = named.queryForList("""
                SELECT media_id FROM course_media_deletion_guard
                WHERE media_id IN (:ids) AND deletion_reserved=TRUE FOR UPDATE
                """, Map.of("ids", ids), Long.class);
        if (!reserved.isEmpty()) throw new ConflictException("关联的视频已进入删除流程，请更换媒资");
    }

    private List<Long> lock(Collection<Long> mediaIds) {
        var ids = mediaIds.stream().distinct().sorted().toList();
        jdbc.batchUpdate("""
                INSERT INTO course_media_deletion_guard(media_id) VALUES(?)
                ON DUPLICATE KEY UPDATE media_id=VALUES(media_id)
                """, ids.stream().map(id -> new Object[] {id}).toList());
        return ids;
    }
}
