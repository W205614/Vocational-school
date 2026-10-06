package com.tianji.exam.controller;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.learning.LearningClient;
import com.tianji.common.autoconfigure.reliability.OperationStore;
import com.tianji.common.exceptions.*;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.service.impl.ExamWorkflowService;
import com.tianji.exam.service.impl.ExamWorkflowService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v2")
public class ExamWorkflowController {
    private final ExamWorkflowService service;private final OperationStore operations;private final CourseClient courses;private final LearningClient learning;private final JdbcTemplate jdbc;
    private final com.tianji.api.client.user.UserClient users;
    public record PublishRequest(long courseId,long sectionId,List<Long> questionIds,Integer passPercent,List<Long> graders) {}
    @PostMapping("/admin/exam-papers") public ResponseEntity<OperationStore.View> publish(@RequestBody PublishRequest form,@RequestHeader("Idempotency-Key") String key) {
        UserContext.requireAdmin();
        var section=courses.sectionInfo(form.sectionId());var course=courses.getCourseInfoById(form.courseId(),false,false);
        if(section==null || !Objects.equals(section.getCourseId(),form.courseId()) || !Integer.valueOf(3).equals(section.getType()) || course==null) throw new BadRequestException("考试小节不属于该课程");
        if(form.graders()!=null && !form.graders().isEmpty()) {
            if(form.graders().size()>100 || form.graders().stream().anyMatch(id->id==null || id<=0))throw new BadRequestException("评分教师参数无效");
            var assigned=users.queryUserByIds(form.graders());
            if(assigned.size()!=new HashSet<>(form.graders()).size() || assigned.stream().anyMatch(u->!Integer.valueOf(3).equals(u.getType())))throw new BadRequestException("评分人必须是有效教师");
        }
        return ResponseEntity.accepted().body(operations.submit(UserContext.requireUser(),"EXAM_PUBLISH",key,form,new Publish(form.courseId(),form.sectionId(),form.questionIds(),form.passPercent(),form.graders(),course.getSectionNum())));
    }
    @GetMapping("/exam-papers") public List<Map<String,Object>> papers(@RequestParam long courseId) {
        UserContext.requireUser();
        if(learning.isLessonValid(courseId)==null) throw new BadRequestException("无课程学习权限");
        return jdbc.queryForList("SELECT id,course_id,section_id,version,total_score,pass_percent FROM exam_paper WHERE course_id=? ORDER BY section_id,version DESC",courseId);
    }
    @PostMapping("/exam-papers/{id}/attempts") public ResponseEntity<OperationStore.View> start(@PathVariable long id,@RequestHeader("Idempotency-Key") String key) {
        UserContext.requireUser();var paper=service.paper(id);
        Long lesson=learning.isLessonValid(((Number)paper.get("course_id")).longValue());
        if(lesson==null) throw new BadRequestException("无课程学习权限");
        return command(key,new Command("START",id,null,lesson,null,null,null,null,null,null));
    }
    @GetMapping("/exam-attempts/{id}") public Map<String,Object> attempt(@PathVariable long id) {return service.view(id,UserContext.requireUser(),false);}
    @PostMapping("/exam-attempts/{id}/submit") public ResponseEntity<OperationStore.View> submit(@PathVariable long id,@RequestBody Command form,@RequestHeader("Idempotency-Key") String key) {
        return command(key,new Command("SUBMIT",null,id,null,form.answers(),null,null,null,null,null));
    }
    @GetMapping("/teacher/exam-attempts") public List<Map<String,Object>> pending() {
        long user=UserContext.requireUser();Long role=UserContext.getRole();
        if(!Set.of(1L,3L).contains(role==null?0L:role)) throw new ForbiddenException("需要评分教师权限");
        return role==1L?jdbc.queryForList("SELECT a.*,p.course_id,p.section_id FROM exam_attempt a JOIN exam_paper p ON p.id=a.paper_id WHERE status='WAIT_GRADING' ORDER BY submitted_at LIMIT 100")
                :jdbc.queryForList("SELECT a.*,p.course_id,p.section_id FROM exam_attempt a JOIN exam_paper p ON p.id=a.paper_id JOIN exam_grader g ON g.paper_id=a.paper_id WHERE g.user_id=? AND a.status='WAIT_GRADING' ORDER BY submitted_at LIMIT 100",user);
    }
    @GetMapping("/teacher/exam-attempts/{id}") public Map<String,Object> grading(@PathVariable long id) {
        long user=UserContext.requireUser();Long role=UserContext.getRole();
        if(!Long.valueOf(1).equals(role) && (!Long.valueOf(3).equals(role) || jdbc.queryForList("SELECT g.user_id FROM exam_grader g JOIN exam_attempt a ON a.paper_id=g.paper_id WHERE a.id=? AND g.user_id=?",id,user).isEmpty())) throw new ForbiddenException("没有评分权限");
        return service.view(id,user,true);
    }
    @PostMapping("/teacher/exam-attempts/{id}/grades") public ResponseEntity<OperationStore.View> grade(@PathVariable long id,@RequestBody Command form,@RequestHeader("Idempotency-Key") String key) {
        return command(key,new Command("GRADE",null,id,null,null,form.questionId(),form.score(),form.version(),form.feedback(),UserContext.getRole()));
    }
    private ResponseEntity<OperationStore.View> command(String key,Command cmd) {return ResponseEntity.accepted().body(operations.submit(UserContext.requireUser(),"EXAM_COMMAND",key,cmd));}
}
