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

    public record Assignment(Long courseId, String title, String instructions, LocalDateTime publishedAt) {}
    public record Configuration(String title, String instructions, boolean publish) {}
    public record Submit(String description, String workUrl, UUID submissionKey) {}
    public record Feedback(Status status, String feedback) {}
    public record StudentAssignment(Long courseId, String title) {}
    public record StudentView(Assignment assignment, boolean canSubmit, List<Submission> submissions) {}
    public record Submission(
            Long id, Long userId, int revision, String description, String workUrl, Status status,
            String feedback, LocalDateTime submittedAt, LocalDateTime reviewedAt) {}
    public record StudentWork(Long userId, String name, Submission latest) {}
    public record SubmissionPage(List<StudentWork> list, long total) {}

    private static final RowMapper<Assignment> ASSIGNMENT = (rs, index) ->
            new Assignment(rs.getLong("course_id"), rs.getString("title"), rs.getString("instructions"),
                    rs.getObject("published_at", LocalDateTime.class));
    private static final RowMapper<Submission> SUBMISSION = (rs, index) ->
            new Submission(rs.getLong("id"), rs.getLong("user_id"), rs.getInt("revision"),
                    rs.getString("description"), rs.getString("work_url"), Status.valueOf(rs.getString("status")),
                    rs.getString("feedback"), rs.getObject("submitted_at", LocalDateTime.class),
                    rs.getObject("reviewed_at", LocalDateTime.class));

    private final JdbcTemplate jdbc;
    private final CourseClient courses;
    private final UserClient users;
    private final LearningEntitlementService entitlements;
    private final LearningHubOperationHandler teaching;
    private final OutboxStore outbox;

    public Assignment configuration(long course) {
        return jdbc.query("SELECT * FROM learning_practice_assignment WHERE course_id=?", ASSIGNMENT, course)
                .stream().findFirst().orElse(null);
    }

    @Transactional
    public Assignment configure(long course, Configuration form) {
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
        if (prior.publishedAt() != null) {
            if (prior.title().equals(title) && prior.instructions().equals(instructions)) return prior;
            throw new ConflictException("作业已发布，任务说明已锁定");
        }
        jdbc.update("""
                UPDATE learning_practice_assignment SET title=?,instructions=?,
                    published_at=IF(?,CURRENT_TIMESTAMP(3),NULL) WHERE course_id=?
                """, title, instructions, form.publish(), course);
        return configuration(course);
    }

    public List<StudentAssignment> assignments(long student) {
        return jdbc.query("""
                SELECT a.course_id,a.title FROM learning_practice_assignment a
                WHERE a.published_at IS NOT NULL AND (
                    EXISTS(SELECT 1 FROM learning_entitlement e
                        WHERE e.user_id=? AND e.course_id=a.course_id AND e.active=1
                        AND (e.expires_at IS NULL OR e.expires_at>CURRENT_TIMESTAMP(3)))
                    OR EXISTS(SELECT 1 FROM learning_practice_submission s
                        WHERE s.user_id=? AND s.course_id=a.course_id AND s.cleaned_at IS NULL))
                ORDER BY a.published_at DESC,a.course_id
                """, (rs, index) -> new StudentAssignment(rs.getLong("course_id"), rs.getString("title")),
                student, student);
    }

    public StudentView student(long course, long student) {
        var submissions = history(course, student);
        boolean active = entitlements.findAvailableReadOnly(student, course) != null;
        if (!active && submissions.isEmpty()) throw new ForbiddenException("无有效课程权益");
        Assignment assignment = published(course);
        boolean canSubmit = active
                && (submissions.isEmpty() || submissions.getFirst().status() == Status.NEEDS_REVISION);
        return new StudentView(assignment, canSubmit, submissions);
    }

    @Transactional
    public Submission submit(long course, long student, Submit form) {
        String description = required(form.description(), 5000, "作品说明");
        String url = httpsUrl(form.workUrl());
        if (form.submissionKey() == null) throw new BadRequestException("缺少提交标识");
        entitlements.require(student, course);
        published(course);
        String key = form.submissionKey().toString();
        var repeated = jdbc.query("""
                SELECT * FROM learning_practice_submission
                WHERE course_id=? AND user_id=? AND submission_key=? FOR UPDATE
                """, SUBMISSION, course, student, key);
        if (!repeated.isEmpty()) {
            Submission prior = repeated.getFirst();
            if (prior.description().equals(description) && Objects.equals(prior.workUrl(), url)) return prior;
            throw new ConflictException("提交标识已用于其他内容");
        }
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
                    (id,course_id,user_id,revision,submission_key,description,work_url,status)
                VALUES(?,?,?,?,?,?,?,'SUBMITTED')
                """, id, course, student, revision, key, description, url);
        return jdbc.query("SELECT * FROM learning_practice_submission WHERE id=?", SUBMISSION, id).getFirst();
    }

    public SubmissionPage teachingWork(long course, long teacher, Long role, PageQuery page) {
        teaching.requireTeacher(teacher, role, course);
        published(course);
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
        published(course);
        return history(course, student);
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

    private Assignment published(long course) {
        Assignment assignment = configuration(course);
        if (assignment == null || assignment.publishedAt() == null) throw new BadRequestException("作业尚未发布");
        return assignment;
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
