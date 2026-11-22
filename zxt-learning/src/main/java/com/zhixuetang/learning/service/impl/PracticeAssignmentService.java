package com.zhixuetang.learning.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhixuetang.api.client.course.CourseClient;
import com.zhixuetang.api.client.user.UserClient;
import com.zhixuetang.api.dto.message.ProductNotice;
import com.zhixuetang.api.dto.user.UserDTO;
import com.zhixuetang.common.autoconfigure.reliability.OutboxStore;
import com.zhixuetang.common.domain.query.PageQuery;
import com.zhixuetang.common.exceptions.BadRequestException;
import com.zhixuetang.common.exceptions.ConflictException;
import com.zhixuetang.common.exceptions.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PracticeAssignmentService {
    public enum Status { SUBMITTED, NEEDS_REVISION, PASSED }

    public record Assignment(
            Long courseId, String title, String instructions, LocalDateTime publishedAt, Integer version) {}
    public record AssignmentVersion(
            int version, String title, String instructions, String changeReason, LocalDateTime effectiveAt) {}
    public record AssignmentView(Assignment assignment, List<AssignmentVersion> versions) {}
    public record Configuration(String title, String instructions, boolean publish) {}
    public record Correction(String title, String instructions, String reason, int expectedVersion) {}
    public record Submit(String description, String workUrl, UUID submissionKey, Integer assignmentVersion) {}
    public record Feedback(Status status, String feedback) {}
    public record StudentAssignment(Long courseId, String title) {}
    public record StudentView(
            Assignment assignment, List<AssignmentVersion> versions, boolean historyOnly,
            boolean canSubmit, List<Submission> submissions) {}
    public record Submission(
            Long id, Long userId, int revision, int assignmentVersion, String description, String workUrl, Status status,
            String feedback, LocalDateTime submittedAt, LocalDateTime reviewedAt) {}
    public record StudentWork(Long userId, String name, Submission latest) {}
    public record SubmissionPage(List<StudentWork> list, long total) {}

    private static final RowMapper<Assignment> ASSIGNMENT = (rs, index) ->
            new Assignment(rs.getLong("course_id"), rs.getString("title"), rs.getString("instructions"),
                    rs.getObject("published_at", LocalDateTime.class), rs.getObject("current_version", Integer.class));
    private static final RowMapper<AssignmentVersion> VERSION = (rs, index) ->
            new AssignmentVersion(rs.getInt("version"), rs.getString("title"), rs.getString("instructions"),
                    rs.getString("change_reason"), rs.getObject("effective_at", LocalDateTime.class));
    private static final RowMapper<Submission> SUBMISSION = (rs, index) ->
            new Submission(rs.getLong("id"), rs.getLong("user_id"), rs.getInt("revision"), rs.getInt("assignment_version"),
                    rs.getString("description"), rs.getString("work_url"), Status.valueOf(rs.getString("status")),
                    rs.getString("feedback"), rs.getObject("submitted_at", LocalDateTime.class),
                    rs.getObject("reviewed_at", LocalDateTime.class));

    private final JdbcTemplate jdbc;
    private final CourseClient courses;
    private final UserClient users;
    private final LearningEntitlementService entitlements;
    private final LearningHubOperationHandler teaching;
    private final OutboxStore outbox;

    @Transactional(readOnly = true)
    public AssignmentView configuration(long course) {
        return new AssignmentView(findAssignment(course), versions(course));
    }

    @Transactional
    public Assignment configure(long course, long admin, Configuration form) {
        if (courses.getCourseInfoById(course, false, false) == null)
            throw new BadRequestException("请先建立已上架课程");
        String title = required(form.title(), 120, "作业标题");
        String instructions = required(form.instructions(), 8000, "任务说明");
        jdbc.update("""
                INSERT INTO learning_practice_assignment(course_id,title,instructions)
                VALUES(?,?,?) ON DUPLICATE KEY UPDATE course_id=VALUES(course_id)
                """, course, title, instructions);
        Assignment prior = jdbc.query(
                "SELECT * FROM learning_practice_assignment WHERE course_id=? FOR UPDATE", ASSIGNMENT, course).getFirst();
        if (prior.version() != null) {
            if (prior.title().equals(title) && prior.instructions().equals(instructions)) return prior;
            throw new ConflictException("作业已发布，请通过发布更正修改任务说明");
        }
        if (form.publish()) {
            publishVersion(course, 1, admin, title, instructions, "首次发布");
            return findAssignment(course);
        }
        jdbc.update("UPDATE learning_practice_assignment SET title=?,instructions=? WHERE course_id=?",
                title, instructions, course);
        return findAssignment(course);
    }

    @Transactional
    public AssignmentView correct(long course, long admin, Correction form) {
        String title = required(form.title(), 120, "作业标题");
        String instructions = required(form.instructions(), 8000, "任务说明");
        String reason = required(form.reason(), 1000, "更正说明");
        if (form.expectedVersion() < 1 || form.expectedVersion() == Integer.MAX_VALUE)
            throw new BadRequestException("作业版本无效");
        Assignment current = requirePublished(jdbc.query(
                "SELECT * FROM learning_practice_assignment WHERE course_id=? FOR UPDATE", ASSIGNMENT, course)
                .stream().findFirst().orElse(null));
        int next = form.expectedVersion() + 1;
        if (current.version() != form.expectedVersion()) {
            var committed = jdbc.query("""
                    SELECT * FROM learning_practice_assignment_version WHERE course_id=? AND version=?
                    """, VERSION, course, next);
            if (!committed.isEmpty()) {
                AssignmentVersion prior = committed.getFirst();
                if (prior.title().equals(title) && prior.instructions().equals(instructions)
                        && prior.changeReason().equals(reason)) return configuration(course);
            }
            throw new ConflictException("作业已有更新，请载入最新版本后再更正");
        }
        if (current.title().equals(title) && current.instructions().equals(instructions))
            throw new BadRequestException("标题或任务说明至少修改一项");
        publishVersion(course, next, admin, title, instructions, reason);
        notifyCorrection(course, next);
        return configuration(course);
    }

    private void notifyCorrection(long course, int version) {
        var recipients = jdbc.queryForList("""
                SELECT l.user_id FROM learning_lesson l
                WHERE l.course_id=? AND EXISTS (
                    SELECT 1 FROM learning_entitlement e
                    WHERE e.user_id=l.user_id AND e.course_id=l.course_id AND e.active=1
                      AND (e.expires_at IS NULL OR e.expires_at>CURRENT_TIMESTAMP(3))
                ) AND COALESCE((
                    SELECT s.status FROM learning_practice_submission s
                    WHERE s.course_id=l.course_id AND s.user_id=l.user_id AND s.cleaned_at IS NULL
                    ORDER BY s.revision DESC LIMIT 1
                ), 'SUBMITTED') <> 'PASSED'
                """, Long.class, course);
        for (long student : recipients) {
            String noticeId = "practice-correction:" + course + ':' + version + ':' + student;
            outbox.enqueue(noticeId, "learning.topic", "learning.notice.product",
                    new ProductNotice(noticeId, student, "实操作业要求已更正",
                            "课程实操作业已更新至第 " + version + " 版，可查看更正内容。",
                            "PRACTICE_CORRECTION", (long) version, course, null));
        }
    }

    private void publishVersion(long course, int version, long admin, String title, String instructions, String reason) {
        jdbc.update("""
                INSERT INTO learning_practice_assignment_version
                    (course_id,version,title,instructions,change_reason,editor_id)
                VALUES(?,?,?,?,?,?)
                """, course, version, title, instructions, reason, admin);
        jdbc.update("""
                UPDATE learning_practice_assignment SET title=?,instructions=?,
                    published_at=COALESCE(published_at,CURRENT_TIMESTAMP(3)),current_version=?
                WHERE course_id=?
                """, title, instructions, version, course);
    }

    public List<StudentAssignment> assignments(long student) {
        return jdbc.query("""
                SELECT a.course_id,CASE WHEN a.active THEN a.title ELSE past.title END AS title
                FROM (
                    SELECT a.course_id,a.title,a.published_at,EXISTS(
                        SELECT 1 FROM learning_entitlement e
                        WHERE e.user_id=? AND e.course_id=a.course_id AND e.active=1
                        AND (e.expires_at IS NULL OR e.expires_at>CURRENT_TIMESTAMP(3))) AS active
                    FROM learning_practice_assignment a WHERE a.current_version IS NOT NULL
                ) a
                LEFT JOIN LATERAL (
                    SELECT v.title FROM learning_practice_submission s
                    JOIN learning_practice_assignment_version v
                        ON v.course_id=s.course_id AND v.version=s.assignment_version
                    WHERE s.user_id=? AND s.course_id=a.course_id AND s.cleaned_at IS NULL
                    ORDER BY s.revision DESC LIMIT 1
                ) past ON NOT a.active
                WHERE a.active OR past.title IS NOT NULL
                ORDER BY a.published_at DESC,a.course_id
                """, (rs, index) -> new StudentAssignment(rs.getLong("course_id"), rs.getString("title")),
                student, student);
    }

    @Transactional(readOnly = true)
    public StudentView student(long course, long student) {
        var submissions = history(course, student);
        boolean active = entitlements.findAvailableReadOnly(student, course) != null;
        if (!active && submissions.isEmpty()) throw new ForbiddenException("无有效课程权益");
        Assignment assignment = requirePublished(findAssignment(course));
        var versions = versions(course);
        if (!active) {
            // Expired learners retain the instructions attached to their work.
            var seen = submissions.stream().map(Submission::assignmentVersion).collect(Collectors.toSet());
            versions = versions.stream().filter(version -> seen.contains(version.version())).toList();
            AssignmentVersion last = versions.getFirst();
            assignment = new Assignment(course, last.title(), last.instructions(), assignment.publishedAt(), last.version());
        }
        boolean canSubmit = active
                && (submissions.isEmpty() || submissions.getFirst().status() == Status.NEEDS_REVISION);
        return new StudentView(assignment, versions, !active, canSubmit, submissions);
    }

    @Transactional
    public Submission submit(long course, long student, Submit form) {
        String description = required(form.description(), 5000, "作品说明");
        String url = httpsUrl(form.workUrl());
        if (form.submissionKey() == null) throw new BadRequestException("缺少提交标识");
        entitlements.require(student, course);
        String key = form.submissionKey().toString();
        var repeated = jdbc.query("""
                SELECT * FROM learning_practice_submission
                WHERE course_id=? AND user_id=? AND submission_key=? FOR UPDATE
                """, SUBMISSION, course, student, key);
        if (!repeated.isEmpty()) {
            Submission prior = repeated.getFirst();
            if (assignmentVersionMatches(form.assignmentVersion(), prior.assignmentVersion())
                    && prior.description().equals(description) && Objects.equals(prior.workUrl(), url)) return prior;
            throw new ConflictException("提交标识已用于其他内容");
        }
        // Keep the assignment pointer stable until the submission references its snapshot.
        Assignment assignment = requirePublished(jdbc.query(
                "SELECT * FROM learning_practice_assignment WHERE course_id=? FOR SHARE", ASSIGNMENT, course)
                .stream().findFirst().orElse(null));
        if (!assignmentVersionMatches(form.assignmentVersion(), assignment.version()))
            throw new ConflictException("任务说明已更正，请载入最新版本后再提交");
        var latest = lockedLatest(course, student);
        if (!latest.isEmpty() && latest.getFirst().status() != Status.NEEDS_REVISION)
            throw new ConflictException("已提交，请等待教师反馈");
        int revision = latest.isEmpty() ? 1 : latest.getFirst().revision() + 1;
        if (latest.isEmpty())
            revision = jdbc.queryForObject("""
                    SELECT COALESCE(MAX(revision),0)+1 FROM learning_practice_submission
                    WHERE course_id=? AND user_id=?
                    """, Integer.class, course, student);
        long id = IdWorker.getId();
        jdbc.update("""
                INSERT INTO learning_practice_submission
                    (id,course_id,user_id,revision,assignment_version,submission_key,description,work_url,status)
                VALUES(?,?,?,?,?,?,?,?,'SUBMITTED')
                """, id, course, student, revision, assignment.version(), key, description, url);
        return jdbc.query("SELECT * FROM learning_practice_submission WHERE id=?", SUBMISSION, id).getFirst();
    }

    public SubmissionPage teachingWork(long course, long teacher, Long role, PageQuery page) {
        teaching.requireTeacher(teacher, role, course);
        int offset = page.from();
        String current = """
                FROM learning_practice_submission s WHERE s.course_id=? AND s.cleaned_at IS NULL
                AND NOT EXISTS(SELECT 1 FROM learning_practice_submission later
                    WHERE later.course_id=s.course_id AND later.user_id=s.user_id AND later.revision>s.revision
                    AND later.cleaned_at IS NULL)
                """;
        long total = jdbc.queryForObject("SELECT COUNT(*) " + current, Long.class, course);
        var rows = jdbc.query("SELECT s.* " + current
                        + " ORDER BY (s.status='SUBMITTED') DESC,s.submitted_at,s.id LIMIT ? OFFSET ?",
                SUBMISSION, course, page.getPageSize(), offset);
        if (rows.isEmpty()) return new SubmissionPage(List.of(), total);
        Map<Long, String> names = users.queryUserByIds(rows.stream().map(Submission::userId).toList()).stream()
                .collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        return new SubmissionPage(rows.stream()
                .map(row -> new StudentWork(row.userId(), names.getOrDefault(row.userId(), "学员 " + row.userId()), row))
                .toList(), total);
    }

    public List<Submission> teachingHistory(long course, long student, long teacher, Long role) {
        teaching.requireTeacher(teacher, role, course);
        requirePublished(findAssignment(course));
        return history(course, student);
    }

    @Transactional(readOnly = true)
    public AssignmentView teachingAssignment(long course, long teacher, Long role) {
        teaching.requireTeacher(teacher, role, course);
        Assignment assignment = findAssignment(course);
        if (assignment == null || assignment.version() == null) return new AssignmentView(null, List.of());
        return new AssignmentView(assignment, versions(course));
    }

    @Transactional
    public Submission review(long course, long id, long teacher, Long role, Feedback form) {
        teaching.requireTeacher(teacher, role, course);
        if (form.status() != Status.NEEDS_REVISION && form.status() != Status.PASSED)
            throw new BadRequestException("请选择通过或需要修改");
        String feedback = required(form.feedback(), 5000, "教师反馈");
        var target = jdbc.query("SELECT * FROM learning_practice_submission WHERE course_id=? AND id=? AND cleaned_at IS NULL",
                SUBMISSION, course, id);
        if (target.isEmpty()) throw new BadRequestException("提交不存在");
        long student = target.getFirst().userId();
        var latest = lockedLatest(course, student);
        if (latest.isEmpty() || latest.getFirst().id() != id) throw new ConflictException("请打开最新提交");
        Submission item = latest.getFirst();
        if (item.status() != Status.SUBMITTED) {
            if (item.status() == form.status() && item.feedback().equals(feedback)) return item;
            throw new ConflictException("这份提交已处理，请刷新");
        }
        jdbc.update("""
                UPDATE learning_practice_submission SET status=?,feedback=?,reviewed_at=CURRENT_TIMESTAMP(3)
                WHERE id=?
                """, form.status().name(), feedback, id);
        String noticeId = "practice-feedback:" + id;
        outbox.enqueue(noticeId, "learning.topic", "learning.notice.product",
                new ProductNotice(noticeId, student, "实操作业已收到反馈",
                        form.status() == Status.PASSED ? "教师已通过你的作业。" : "请查看老师的意见并修改重交。",
                        "PRACTICE", course, course, null));
        return jdbc.query("SELECT * FROM learning_practice_submission WHERE id=?", SUBMISSION, id).getFirst();
    }

    private List<Submission> lockedLatest(long course, long student) {
        return jdbc.query("""
                SELECT * FROM learning_practice_submission
                WHERE course_id=? AND user_id=? AND cleaned_at IS NULL
                ORDER BY revision DESC LIMIT 1 FOR UPDATE
                """, SUBMISSION, course, student);
    }

    private List<Submission> history(long course, long student) {
        return jdbc.query("""
                SELECT * FROM learning_practice_submission
                WHERE course_id=? AND user_id=? AND cleaned_at IS NULL ORDER BY revision DESC
                """, SUBMISSION, course, student);
    }

    private Assignment findAssignment(long course) {
        return jdbc.query("SELECT * FROM learning_practice_assignment WHERE course_id=?", ASSIGNMENT, course)
                .stream().findFirst().orElse(null);
    }

    private List<AssignmentVersion> versions(long course) {
        return jdbc.query("""
                SELECT * FROM learning_practice_assignment_version WHERE course_id=? ORDER BY version DESC
                """, VERSION, course);
    }

    private static Assignment requirePublished(Assignment assignment) {
        if (assignment == null || assignment.version() == null) throw new BadRequestException("作业尚未发布");
        return assignment;
    }

    private static boolean assignmentVersionMatches(Integer requested, int version) {
        // Clients predating versioning can still submit or replay work under the original instructions.
        return requested == null ? version == 1 : requested == version;
    }

    private static String required(String text, int max, String label) {
        if (text == null || text.isBlank() || text.strip().length() > max)
            throw new BadRequestException(label + "须填写，最多 " + max + " 字");
        return text.strip();
    }

    private static String httpsUrl(String text) {
        if (text == null || text.isBlank()) return null;
        String url = text.strip();
        URI parsed;
        try {
            parsed = URI.create(url);
        } catch (IllegalArgumentException invalid) {
            throw new BadRequestException("请输入有效的 HTTPS 作品链接");
        }
        if (url.length() > 2048 || !"https".equalsIgnoreCase(parsed.getScheme())
                || parsed.getHost() == null || parsed.getUserInfo() != null)
            throw new BadRequestException("请输入有效的 HTTPS 作品链接");
        return url;
    }
}
