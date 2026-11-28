package com.zhixuetang.learning.classroom;

import com.zhixuetang.api.client.learning.ClassroomClient.ExamAccess;
import com.zhixuetang.api.client.learning.ClassroomClient.ExamAccessRequest;
import com.zhixuetang.api.client.learning.ClassroomClient.Seat;
import com.zhixuetang.api.client.learning.ClassroomExamClient;
import com.zhixuetang.common.utils.InternalAuth;
import com.zhixuetang.common.utils.UserContext;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import static com.zhixuetang.learning.classroom.ClassroomActivities.*;
import static com.zhixuetang.learning.classroom.ClassroomGrades.*;
import static com.zhixuetang.learning.classroom.Classrooms.*;

@RestController
@RequiredArgsConstructor
public class ClassroomController {
    private static final String ADMIN = "/api/v2/admin/learning-hub/classrooms";
    private static final String TEACHER = "/api/v2/teacher/learning-hub/classrooms";
    private static final String STUDENT = "/api/v2/learning-hub/classrooms";

    public record StateChange(State state, String reason) {}
    public record StaffChange(Duty duty) {}
    public record AddMember(long userId, String reason) {}
    public record MoveMember(Long targetClassId, String reason) {}
    public record CopySchedule(long sourceClassId, Map<Long, Long> replacementPapers) {}

    private final Classrooms rooms;
    private final ClassroomActivities activities;
    private final ClassroomGrades grades;

    @GetMapping(ADMIN)
    public List<Classroom> administered() { return rooms.administered(); }

    @PostMapping(ADMIN)
    public Classroom create(@RequestBody Setup form) { return rooms.save(null, form); }

    @PutMapping(ADMIN + "/{id}")
    public Classroom update(@PathVariable long id, @RequestBody Setup form) { return rooms.save(id, form); }

    @PutMapping(ADMIN + "/{id}/state")
    public Classroom state(@PathVariable long id, @RequestBody StateChange form) {
        if (form.state() == null) throw new com.zhixuetang.common.exceptions.BadRequestException("请选择班级状态");
        return rooms.changeState(id, form.state(), form.reason());
    }

    @PutMapping(ADMIN + "/{id}/staff/{user}")
    public void assign(@PathVariable long id, @PathVariable long user, @RequestBody StaffChange form) {
        rooms.assign(id, user, form.duty());
    }

    @PostMapping(ADMIN + "/{id}/members")
    public Membership add(@PathVariable long id, @RequestBody AddMember form) {
        UserContext.requireAdmin();
        return rooms.enroll(id, form.userId(), true, form.reason());
    }

    @PutMapping(ADMIN + "/{id}/members/{member}")
    public void move(@PathVariable long id, @PathVariable long member, @RequestBody MoveMember form) {
        rooms.move(id, member, form.targetClassId(), form.reason());
    }

    @PostMapping(ADMIN + "/{id}/copy-schedule")
    public List<Activity> copy(@PathVariable long id, @RequestBody CopySchedule form) {
        return activities.copy(id, form.sourceClassId(), form.replacementPapers());
    }

    @GetMapping(TEACHER)
    public List<Classroom> assigned() { return rooms.assigned(); }

    @GetMapping(TEACHER + "/{id}/staff")
    public List<Staff> staff(@PathVariable long id) { return rooms.staff(id); }

    @GetMapping(TEACHER + "/{id}/members")
    public List<RosterRow> roster(@PathVariable long id) { return rooms.roster(id); }

    @GetMapping(TEACHER + "/{id}/pending")
    public Pending pending(@PathVariable long id) { return activities.pending(id); }

    @GetMapping(TEACHER + "/{id}/papers")
    public List<ClassroomExamClient.Paper> papers(@PathVariable long id) { return activities.papers(id); }

    @GetMapping(TEACHER + "/{id}/sections")
    public List<Section> sections(@PathVariable long id) { return activities.sections(id); }

    @GetMapping(TEACHER + "/{id}/activities")
    public List<Activity> activities(@PathVariable long id) { return activities.teaching(id); }

    @PostMapping(TEACHER + "/{id}/activities")
    public Activity publish(@PathVariable long id, @RequestBody Form form) { return activities.publish(id, form); }

    @GetMapping(TEACHER + "/{id}/activities/{activity}/versions")
    public List<Version> teachingVersions(@PathVariable long id, @PathVariable long activity) {
        return activities.versions(id, activity, true);
    }

    @PutMapping(TEACHER + "/{id}/activities/{activity}/correction")
    public Activity correct(@PathVariable long id, @PathVariable long activity, @RequestBody Correction form) {
        return activities.correct(id, activity, form);
    }

    @PutMapping(TEACHER + "/{id}/activities/{activity}/extension")
    public void extend(@PathVariable long id, @PathVariable long activity, @RequestBody Extension form) {
        activities.extend(id, activity, form);
    }

    @GetMapping(TEACHER + "/{id}/activities/{activity}/submissions")
    public List<Submission> submissions(@PathVariable long id, @PathVariable long activity) {
        return activities.teachingSubmissions(id, activity);
    }

    @PutMapping(TEACHER + "/{id}/activities/{activity}/submissions/{submission}")
    public Submission review(@PathVariable long id, @PathVariable long activity, @PathVariable long submission,
                             @RequestBody Review form) {
        return activities.review(id, activity, submission, form);
    }

    @GetMapping(TEACHER + "/{id}/discussion")
    public List<Discussion> teachingDiscussion(@PathVariable long id, @RequestParam(required = false) Long before) {
        return activities.discussion(id, true, before);
    }

    @PostMapping(TEACHER + "/{id}/discussion")
    public Discussion teachingReply(@PathVariable long id, @RequestBody DiscussionForm form) {
        return activities.discuss(id, true, form);
    }

    @GetMapping(TEACHER + "/{id}/grades")
    public List<Report> teachingGrades(@PathVariable long id) { return grades.teaching(id); }

    @PostMapping(TEACHER + "/{id}/activities/{activity}/grades")
    public void publishGrade(@PathVariable long id, @PathVariable long activity, @RequestBody Publication form) {
        grades.publish(id, activity, form);
    }

    @PutMapping(TEACHER + "/{id}/activities/{activity}/grades")
    public void changeGrade(@PathVariable long id, @PathVariable long activity, @RequestBody Change form) {
        grades.change(id, activity, form);
    }

    @GetMapping(value = TEACHER + "/{id}/grades/export", produces = "text/csv")
    public ResponseEntity<ByteArrayResource> export(@PathVariable long id) {
        rooms.staffAccess(id, true);
        var data = new StringBuilder("\uFEFF学生编号,学生姓名,班籍,考核项,权重,分数,总评,通过\r\n");
        for (Report report : grades.teaching(id)) {
            for (Item item : report.items()) {
                String total = report.result() == null ? "" : report.result().totalPercent().toPlainString();
                String passed = report.result() == null ? "" : report.result().passed() ? "通过" : "未通过";
                data.append(report.userId()).append(',').append(csv(report.name())).append(',')
                        .append(report.membershipState()).append(',').append(csv(item.title())).append(',')
                        .append(item.weight()).append(',').append(item.state() == GradeState.PUBLISHED ? item.score() : "")
                        .append(',').append(total).append(',').append(passed).append("\r\n");
            }
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=classroom-" + id + "-grades.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(new ByteArrayResource(data.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @PostMapping(TEACHER + "/{id}/archive")
    public void archive(@PathVariable long id) { grades.archive(id); }

    @GetMapping("/api/v2/public/courses/{course}/classrooms")
    public List<Classroom> available(@PathVariable long course) { return rooms.available(course); }

    @GetMapping(STUDENT)
    public List<ClassMembership> mine() { return rooms.mine(); }

    @PostMapping(STUDENT + "/{id}/enrollment")
    public Membership enroll(@PathVariable long id) { return rooms.enroll(id, student(), false, null); }

    @GetMapping(STUDENT + "/{id}")
    public StudyView study(@PathVariable long id) { return activities.study(id); }

    @GetMapping(STUDENT + "/{id}/activities/{activity}/versions")
    public List<Version> versions(@PathVariable long id, @PathVariable long activity) {
        return activities.versions(id, activity, false);
    }

    @PostMapping(STUDENT + "/{id}/activities/{activity}/submissions")
    public Submission submit(@PathVariable long id, @PathVariable long activity, @RequestBody Submit form) {
        return activities.submit(id, activity, form);
    }

    @GetMapping(STUDENT + "/{id}/activities/{activity}/submissions/{member}")
    public List<Submission> ownSubmissions(@PathVariable long id, @PathVariable long activity, @PathVariable long member) {
        return activities.ownSubmissions(id, activity, member);
    }

    @GetMapping(STUDENT + "/{id}/discussion")
    public List<Discussion> discussion(@PathVariable long id, @RequestParam(required = false) Long before) {
        return activities.discussion(id, false, before);
    }

    @PostMapping(STUDENT + "/{id}/discussion")
    public Discussion discuss(@PathVariable long id, @RequestBody DiscussionForm form) {
        return activities.discuss(id, false, form);
    }

    @GetMapping(STUDENT + "/{id}/members/{member}/grades")
    public Report ownGrades(@PathVariable long id, @PathVariable long member) { return grades.own(id, member); }

    @Hidden
    @PostMapping("/internal/v1/learning/classrooms/seats")
    public void reserve(@RequestBody Seat seat) {
        InternalAuth.requireService();
        rooms.reserve(seat.classId(), seat.courseId(), seat.userId(), seat.orderId());
    }

    @Hidden
    @PostMapping("/internal/v1/learning/classrooms/exam-access")
    public ExamAccess examAccess(@RequestBody ExamAccessRequest request) {
        InternalAuth.requireService();
        return activities.examAccess(request);
    }

    private static String csv(String value) {
        String text = value == null ? "" : value;
        if (text.stripLeading().matches("(?s)^[=+@-].*")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
