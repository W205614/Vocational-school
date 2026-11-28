package com.zhixuetang.learning.classroom;

import com.zhixuetang.api.client.learning.ClassroomExamClient;
import com.zhixuetang.common.exceptions.BadRequestException;
import com.zhixuetang.common.exceptions.ConflictException;
import com.zhixuetang.common.exceptions.ForbiddenException;
import com.zhixuetang.common.utils.UserContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.zhixuetang.learning.classroom.Classrooms.*;
import static com.zhixuetang.learning.classroom.ClassroomActivities.*;

@Service
@RequiredArgsConstructor
public class ClassroomGrades {
    public enum GradeState { NOT_SUBMITTED, NEEDS_REVISION, IN_PROGRESS, PENDING_REVIEW, READY, PUBLISHED }
    public record Publication(Long memberId, String feedback) {}
    public record Change(Long memberId, int expectedScore, int score, String feedback, String reason) {}
    @Schema(name = "ClassroomGradeItem")
    public record Item(Long activityId, String title, Kind kind, int weight, GradeState state,
                       Integer score, String feedback) {}
    @Schema(name = "ClassroomGradeResult")
    public record Result(BigDecimal totalPercent, boolean passed, LocalDateTime finalizedAt) {}
    @Schema(name = "ClassroomGradeReport")
    public record Report(Long memberId, Long userId, String name, MemberState membershipState,
                         List<Item> items, Result result) {}
    private record Published(int score, String feedback) {}
    private record Candidate(GradeState state, Integer score, String feedback) {}

    private final Classrooms rooms;
    private final ClassroomActivities activities;
    private final ClassroomExamClient exams;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Report own(long id, long memberId) {
        long user = student();
        Membership member = rooms.member(id, memberId);
        if (member.userId() != user) throw new ForbiddenException("只能查看本人的成绩");
        if (member.state() == MemberState.HELD || member.state() == MemberState.RELEASED)
            throw new ConflictException("该报名尚未形成班籍");
        return report(member, null, graded(id), true, Map.of());
    }

    @Transactional(readOnly = true)
    public List<Report> teaching(long id) {
        rooms.staffAccess(id, false);
        var roster = rooms.roster(id);
        var graded = graded(id);
        var results = examResults(graded);
        return roster.stream().map(row -> report(rooms.member(id, row.memberId()), row.name(), graded, false, results))
                .toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void publish(long id, long activityId, Publication form) {
        Classroom room = rooms.locked(id);
        rooms.staffAccess(id, true);
        editable(room);
        var activity = activities.get(id, activityId);
        if (form.memberId() == null || activity.weight() <= 0) throw new BadRequestException("请选择考核和学生");
        var member = rooms.member(id, form.memberId());
        if (member.state() == MemberState.HELD || member.state() == MemberState.RELEASED)
            throw new ConflictException("学生尚未入班");
        if (published(member.id(), activityId) != null) throw new ConflictException("成绩已发布，请使用更正");
        if (activity.kind() == Kind.EXAM && !activities.releasePaper(activity.paperId()))
            throw new ConflictException("请在复用该试卷的考试窗口全部截止后发布成绩");
        var candidate = candidate(activity, member.id(), examResults(List.of(activity)));
        if (candidate.state() != GradeState.READY)
            throw new ConflictException("请先完成批改");
        record(room, member, activity, candidate.score(),
                form.feedback() == null ? candidate.feedback() : feedback(form.feedback()));
    }

    @Transactional
    public void change(long id, long activityId, Change form) {
        Classroom room = rooms.locked(id);
        rooms.staffAccess(id, true);
        if (form.memberId() == null || !score(form.score()))
            throw new BadRequestException("请选择学生并填写 0 到 100 的分数");
        var member = rooms.member(id, form.memberId());
        var activity = activities.get(id, activityId);
        var previous = published(member.id(), activityId);
        if (previous == null || previous.score() != form.expectedScore())
            throw new ConflictException("已发布成绩已变化，请刷新");
        String reason = required(form.reason(), 500, "更正原因");
        long actor = UserContext.requireUser();
        jdbc.update("""
                INSERT INTO teaching_class_grade_history(member_id,activity_id,user_id,old_score,new_score,
                old_feedback,reason,changed_by) VALUES(?,?,?,?,?,?,?,?)
                """, member.id(), activity.id(), member.userId(), previous.score(), form.score(),
                previous.feedback(), reason, actor);
        jdbc.update("""
                UPDATE teaching_class_grade SET score=?,feedback=?,published_by=?,published_at=NOW(3)
                WHERE member_id=? AND activity_id=?
                """, form.score(), feedback(form.feedback()), actor, member.id(), activityId);
        rooms.audit(id, actor, member.userId(), "GRADE_CHANGED", reason);
        if (result(member.id()) != null) finalizeResult(room, member, actor);
        activities.notice("class:grade-change:" + member.id() + ":" + activityId + ":" +
                jdbc.queryForObject("""
                        SELECT MAX(id) FROM teaching_class_grade_history WHERE member_id=? AND activity_id=?
                        """, Long.class, member.id(), activityId),
                room, member.userId(), "班级成绩已更正", activity.title() + "：" + reason);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void archive(long id) {
        Classroom room = rooms.locked(id);
        rooms.staffAccess(id, true);
        editable(room);
        if (room.state() == State.DRAFT || room.endsAt().isAfter(LocalDateTime.now()))
            throw new ConflictException("请在班级结束后结班");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM teaching_class_member WHERE class_id=? AND state='HELD'",
                Integer.class, id) != 0)
            throw new ConflictException("请先处理尚未结算的班级订单");
        List<Activity> graded = graded(id);
        if (graded.stream().mapToInt(Activity::weight).sum() != 100)
            throw new ConflictException("结班前考核总权重须为 100%");
        var results = examResults(graded);
        if (graded.stream().filter(a -> a.kind() == Kind.EXAM).map(Activity::paperId).distinct().sorted()
                .anyMatch(paper -> !activities.releasePaper(paper)))
            throw new ConflictException("仍有班级使用相同试卷考试，请等待相关窗口关闭");
        long actor = UserContext.requireUser();
        for (var row : rooms.roster(id)) {
            if (row.state() != MemberState.ACTIVE) continue;
            var member = rooms.member(id, row.memberId());
            for (Activity activity : graded) {
                if (published(member.id(), activity.id()) != null) continue;
                Candidate item = candidate(activity, member.id(), results);
                switch (item.state()) {
                    case NOT_SUBMITTED, IN_PROGRESS -> record(room, member, activity, 0, "截止时未提交");
                    case READY -> record(room, member, activity, item.score(), item.feedback());
                    case NEEDS_REVISION -> record(room, member, activity, 0, "截止时未提交修订");
                    case PENDING_REVIEW -> throw new ConflictException(
                            "还有待处理的考核，请先完成批改");
                    default -> throw new IllegalStateException("Unexpected grade state");
                }
            }
            finalizeResult(room, member, actor);
        }
        jdbc.update("""
                UPDATE teaching_class_member SET state='COMPLETED',ended_at=NOW(3) WHERE class_id=? AND state='ACTIVE'
                """, id);
        jdbc.update("UPDATE teaching_class SET state='ARCHIVED' WHERE id=?", id);
        rooms.audit(id, actor, null, "CLASS_ARCHIVED", null);
    }

    private void record(Classroom room, Membership member, Activity activity, int score, String feedback) {
        jdbc.update("""
                INSERT INTO teaching_class_grade(member_id,activity_id,user_id,score,feedback,published_by)
                VALUES(?,?,?,?,?,?)
                """, member.id(), activity.id(), member.userId(), score, feedback, UserContext.requireUser());
        rooms.audit(room.id(), UserContext.requireUser(), member.userId(), "GRADE_PUBLISHED", Long.toString(activity.id()));
        activities.notice("class:grade:" + member.id() + ":" + activity.id(),
                room, member.userId(), "班级成绩已发布", activity.title() + "已出分。");
    }

    private void finalizeResult(Classroom room, Membership member, long actor) {
        BigDecimal total = jdbc.queryForObject("""
                SELECT ROUND(SUM(g.score*a.weight)/100.0,2) FROM teaching_class_grade g
                JOIN teaching_class_activity a ON a.id=g.activity_id WHERE g.member_id=? AND a.class_id=?
                """, BigDecimal.class, member.id(), room.id());
        boolean passed = total.compareTo(BigDecimal.valueOf(room.passPercent())) >= 0;
        jdbc.update("""
                INSERT INTO teaching_class_result(member_id,class_id,user_id,total_percent,passed)
                VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE total_percent=?,passed=?
                """, member.id(), room.id(), member.userId(), total, passed, total, passed);
        rooms.audit(room.id(), actor, member.userId(), "RESULT_FINALIZED", total.toPlainString());
    }

    private Report report(Membership member, String name, List<Activity> graded, boolean own,
                          Map<Long, List<ClassroomExamClient.ExamResult>> results) {
        List<Item> items = graded.stream().map(activity -> {
            Published grade = published(member.id(), activity.id());
            if (grade != null) return new Item(activity.id(), activity.title(), activity.kind(), activity.weight(),
                    GradeState.PUBLISHED, grade.score(), grade.feedback());
            if (own && activity.kind() == Kind.EXAM)
                return new Item(activity.id(), activity.title(), activity.kind(), activity.weight(),
                        GradeState.IN_PROGRESS, null, null);
            Candidate candidate = candidate(activity, member.id(), results);
            return new Item(activity.id(), activity.title(), activity.kind(), activity.weight(), candidate.state(),
                    own ? null : candidate.score(), own && candidate.state() != GradeState.NEEDS_REVISION ? null : candidate.feedback());
        }).toList();
        return new Report(member.id(), member.userId(), name, member.state(), items, result(member.id()));
    }

    private Candidate candidate(Activity activity, long member,
                                Map<Long, List<ClassroomExamClient.ExamResult>> results) {
        if (activity.kind() == Kind.EXAM) {
            var exam = results.getOrDefault(activity.id(), List.of()).stream()
                    .filter(item -> item.memberId() == member).findFirst();
            if (exam.isEmpty()) return new Candidate(GradeState.NOT_SUBMITTED, null, null);
            var item = exam.get();
            if (item.pending()) return new Candidate(GradeState.PENDING_REVIEW, null, null);
            if (item.bestPercent() == null) return new Candidate(GradeState.IN_PROGRESS, null, null);
            return new Candidate(GradeState.READY, item.bestPercent(), null);
        }
        var submissions = activities.submissions(activity.id(), member);
        if (submissions.isEmpty()) return new Candidate(GradeState.NOT_SUBMITTED, null, null);
        var work = submissions.getFirst();
        return switch (work.state()) {
            case SUBMITTED -> new Candidate(GradeState.PENDING_REVIEW, null, null);
            case NEEDS_REVISION -> new Candidate(GradeState.NEEDS_REVISION, null, work.feedback());
            case REVIEWED -> new Candidate(GradeState.READY, work.score(), work.feedback());
        };
    }

    private Published published(long member, long activity) {
        return jdbc.query("""
                SELECT score,feedback FROM teaching_class_grade WHERE member_id=? AND activity_id=?
                """, (rs, row) -> new Published(rs.getInt(1), rs.getString(2)), member, activity)
                .stream().findFirst().orElse(null);
    }

    private Result result(long member) {
        return jdbc.query("""
                SELECT total_percent,passed,finalized_at FROM teaching_class_result WHERE member_id=?
                """, (rs, row) -> new Result(rs.getBigDecimal(1), rs.getBoolean(2),
                rs.getObject(3, LocalDateTime.class)), member).stream().findFirst().orElse(null);
    }

    private List<Activity> graded(long id) {
        return activities.all(id).stream().filter(a -> a.weight() > 0).toList();
    }

    private Map<Long, List<ClassroomExamClient.ExamResult>> examResults(List<Activity> graded) {
        var result = new java.util.HashMap<Long, List<ClassroomExamClient.ExamResult>>();
        for (Activity item : graded)
            if (item.kind() == Kind.EXAM) result.put(item.id(), exams.results(item.id()));
        return result;
    }

    private static String feedback(String text) {
        return text == null || text.isBlank() ? null : required(text, 5000, "评语");
    }
}
