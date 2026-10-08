package com.tianji.exam.service.impl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.common.autoconfigure.reliability.*;
import com.tianji.common.exceptions.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
@Service @RequiredArgsConstructor
public class ExamWorkflowService implements OperationHandler {
    private final JdbcTemplate jdbc;private final JsonMapper json;private final OutboxStore outbox;
    private final com.tianji.api.client.learning.LearningClient learning;
    public record Publish(long courseId,long sectionId,List<Long> questionIds,Integer passPercent,List<Long> graders,Integer sectionCount) {}
    public record Command(String action,Long paperId,Long attemptId,Long lessonId,Map<Long,String> answers,Long questionId,Integer score,Long version,String feedback,Long actorRole) {}
    @Override public String kind() {return "EXAM_COMMAND";}
    @Transactional public Map<String,Object> publish(Publish form) {
        if(form.questionIds()==null || form.questionIds().isEmpty() || form.questionIds().size()>100 || new HashSet<>(form.questionIds()).size()!=form.questionIds().size() ||
                form.sectionCount()==null || form.sectionCount()<1 || form.passPercent()!=null && (form.passPercent()<1 || form.passPercent()>100)) throw new BadRequestException("试卷参数无效");
        jdbc.update("INSERT INTO exam_paper_family(course_id,section_id) VALUES(?,?) ON DUPLICATE KEY UPDATE latest_version=latest_version",form.courseId(),form.sectionId());
        int version=jdbc.queryForObject("SELECT latest_version FROM exam_paper_family WHERE course_id=? AND section_id=? FOR UPDATE",Integer.class,form.courseId(),form.sectionId())+1;
        List<Map<String,Object>> questions=new ArrayList<>();int total=0;
        for(Long id:form.questionIds()) {
            var rows=jdbc.queryForList("SELECT q.id,q.name,q.type,q.score,d.options,d.answer,d.analysis FROM question q JOIN question_detail d ON d.id=q.id JOIN question_biz b ON b.question_id=q.id WHERE q.id=? AND b.biz_id=?",id,form.sectionId());
            if(rows.isEmpty()) throw new BadRequestException("题目未关联到该考试小节");
            var q=rows.getFirst();int score=((Number)q.get("score")).intValue(),type=((Number)q.get("type")).intValue();
            if(score<=0 || type<1 || type>5) throw new BadRequestException("题目类型或分数无效");
            if(type<5 && (q.get("answer")==null || answerSet(q.get("answer").toString()).isEmpty())) throw new BadRequestException("客观题答案缺失");
            if(type==5 && (form.graders()==null || form.graders().isEmpty())) throw new BadRequestException("主观题需指定评分教师");
            total=Math.addExact(total,score);questions.add(q);
        }
        long id=IdWorker.getId();
        jdbc.update("INSERT INTO exam_paper(id,course_id,section_id,version,total_score,pass_percent,section_count) VALUES(?,?,?,?,?,?,?)",id,form.courseId(),form.sectionId(),version,total,form.passPercent()==null?60:form.passPercent(),form.sectionCount());
        int position=0;
        for(var q:questions) jdbc.update("INSERT INTO exam_paper_question(paper_id,question_id,position,name,type,score,options,answer,analysis) VALUES(?,?,?,?,?,?,?,?,?)",id,q.get("id"),++position,q.get("name"),q.get("type"),q.get("score"),q.get("options"),q.get("answer"),q.get("analysis"));
        if(form.graders()!=null) for(Long user:new HashSet<>(form.graders())) jdbc.update("INSERT INTO exam_grader(paper_id,user_id) VALUES(?,?)",id,user);
        jdbc.update("UPDATE exam_paper_family SET latest_version=? WHERE course_id=? AND section_id=?",version,form.courseId(),form.sectionId());
        return jdbc.queryForMap("SELECT * FROM exam_paper WHERE id=?",id);
    }
    @Override public Object execute(String operation,long user,String payload) {
        Command cmd=json.readValue(payload,Command.class);
        return switch(cmd.action()) {
            case "START" -> start(user,cmd);
            case "SUBMIT" -> submit(user,cmd);
            case "GRADE" -> grade(user,cmd);
            default -> throw new BadRequestException("考试操作无效");
        };
    }
    private Map<String,Object> start(long user,Command cmd) {
        if(cmd.lessonId()==null || cmd.paperId()==null) throw new BadRequestException("试卷和课表不能为空");
        requireEntitlement(user,paper(cmd.paperId()),cmd.lessonId());
        // The paper row serializes start/retake against all existing attempts.
        var papers=jdbc.queryForList("SELECT id FROM exam_paper WHERE id=? FOR UPDATE",cmd.paperId());
        if(papers.isEmpty()) throw new BadRequestException("试卷不存在");
        // A locking read sees commits made while waiting for the paper lock, even
        // when this transaction already has a REPEATABLE READ snapshot.
        var active=jdbc.queryForList("SELECT id FROM exam_attempt WHERE user_id=? AND paper_id=? AND status IN('IN_PROGRESS','WAIT_GRADING') FOR UPDATE",user,cmd.paperId());
        if(!active.isEmpty()) return view(((Number)active.getFirst().get("id")).longValue(),user,false);
        if(!jdbc.queryForList("SELECT id FROM exam_attempt WHERE user_id=? AND paper_id=? AND passed=1 LIMIT 1 FOR UPDATE",user,cmd.paperId()).isEmpty()) throw new BadRequestException("已通过该试卷");
        long id=IdWorker.getId();
        jdbc.update("INSERT INTO exam_attempt(id,paper_id,user_id,lesson_id) VALUES(?,?,?,?)",id,cmd.paperId(),user,cmd.lessonId());
        return view(id,user,false);
    }
    private Map<String,Object> submit(long user,Command cmd) {
        var candidates=jdbc.queryForList("SELECT paper_id,lesson_id FROM exam_attempt WHERE id=? AND user_id=?",cmd.attemptId(),user);
        if(candidates.isEmpty())throw new BadRequestException("考试记录不存在");
        var candidate=candidates.getFirst();requireEntitlement(user,paper(((Number)candidate.get("paper_id")).longValue()),((Number)candidate.get("lesson_id")).longValue());
        var attempt=lockAttempt(cmd.attemptId());
        if(((Number)attempt.get("user_id")).longValue()!=user) throw new BadRequestException("考试记录不存在");
        if(!"IN_PROGRESS".equals(attempt.get("status"))) return view(cmd.attemptId(),user,false);
        if(cmd.version()!=null) {
            var drafts=jdbc.queryForList("SELECT version FROM exam_draft WHERE attempt_id=?",cmd.attemptId());
            long version=drafts.isEmpty()?0:((Number)drafts.getFirst().get("version")).longValue();
            if(cmd.version()!=version) throw new ConflictException("草稿已在其他页面更新，请载入最新草稿后提交");
        }
        Map<Long,String> answers=cmd.answers()==null?Map.of():cmd.answers();
        var questions=jdbc.queryForList("SELECT * FROM exam_paper_question WHERE paper_id=? ORDER BY position",attempt.get("paper_id"));
        Set<Long> valid=new HashSet<>();questions.forEach(q->valid.add(((Number)q.get("question_id")).longValue()));
        if(!valid.containsAll(answers.keySet())) throw new BadRequestException("答案包含试卷外题目");
        boolean subjective=false;
        for(var q:questions) {
            long id=((Number)q.get("question_id")).longValue();int type=((Number)q.get("type")).intValue();
            String response=Objects.toString(answers.get(id),"");
            if(response.length()>10000) throw new BadRequestException("答案过长");
            Integer score=type==5?null:scoreObjective(response,q.get("answer").toString(),((Number)q.get("score")).intValue());
            subjective|=type==5;
            jdbc.update("INSERT INTO exam_answer(attempt_id,question_id,response,score) VALUES(?,?,?,?)",cmd.attemptId(),id,response,score);
        }
        jdbc.update("UPDATE exam_attempt SET status='WAIT_GRADING',submitted_at=CURRENT_TIMESTAMP(3),version=version+1 WHERE id=?",cmd.attemptId());
        if(!subjective) finish(cmd.attemptId(),attempt);
        return view(cmd.attemptId(),user,false);
    }
    private Map<String,Object> grade(long user,Command cmd) {
        var attempt=lockAttempt(cmd.attemptId());
        long paper=((Number)attempt.get("paper_id")).longValue();
        boolean admin=Long.valueOf(1).equals(cmd.actorRole());
        if(!admin && (!Long.valueOf(3).equals(cmd.actorRole()) || jdbc.queryForList("SELECT user_id FROM exam_grader WHERE paper_id=? AND user_id=?",paper,user).isEmpty())) throw new BadRequestException("没有该试卷的评分权限");
        if(!"WAIT_GRADING".equals(attempt.get("status"))) throw new ConflictException("考试已评分或尚未提交");
        var question=jdbc.queryForList("SELECT score FROM exam_paper_question WHERE paper_id=? AND question_id=? AND type=5",paper,cmd.questionId());
        if(question.isEmpty() || cmd.score()==null || cmd.score()<0 || cmd.score()>((Number)question.getFirst().get("score")).intValue() || cmd.version()==null) throw new BadRequestException("评分参数无效");
        if(cmd.feedback()!=null && cmd.feedback().length()>2000) throw new BadRequestException("评分意见过长");
        if(jdbc.update("UPDATE exam_answer SET score=?,graded_by=?,feedback=?,version=version+1 WHERE attempt_id=? AND question_id=? AND version=? AND score IS NULL",cmd.score(),user,cmd.feedback(),cmd.attemptId(),cmd.questionId(),cmd.version())!=1)
            throw new ConflictException("该答案已被评分或版本变化");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM exam_answer WHERE attempt_id=? AND score IS NULL",Long.class,cmd.attemptId())==0) finish(cmd.attemptId(),attempt);
        return view(cmd.attemptId(),user,true);
    }
    private void finish(long id,Map<String,Object> attempt) {
        var paper=paper(((Number)attempt.get("paper_id")).longValue());
        int score=jdbc.queryForObject("SELECT COALESCE(SUM(score),0) FROM exam_answer WHERE attempt_id=?",Integer.class,id);
        boolean passed=(long)score*100>=((Number)paper.get("total_score")).longValue()*((Number)paper.get("pass_percent")).intValue();
        jdbc.update("UPDATE exam_attempt SET status='FINISHED',score=?,passed=?,graded_at=CURRENT_TIMESTAMP(3),version=version+1 WHERE id=? AND status='WAIT_GRADING'",score,passed,id);
        if(passed) outbox.enqueue("exam:"+id+":passed","learning.topic","exam.passed",
                Map.of("attemptId",id,"userId",attempt.get("user_id"),"lessonId",attempt.get("lesson_id"),"courseId",paper.get("course_id"),"sectionId",paper.get("section_id"),"sectionCount",paper.get("section_count")));
    }
    private Map<String,Object> lockAttempt(Long id) {
        if(id==null) throw new BadRequestException("考试标识不能为空");
        var rows=jdbc.queryForList("SELECT * FROM exam_attempt WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new BadRequestException("考试记录不存在");return rows.getFirst();
    }
    private void requireEntitlement(long user,Map<String,Object> paper,long lesson) {
        // The durable operation owner, rather than the worker thread's last caller,
        // is the only identity allowed to validate this student's enrollment.
        var oldUser=com.tianji.common.utils.UserContext.getUser();var oldRole=com.tianji.common.utils.UserContext.getRole();
        var oldSession=com.tianji.common.utils.UserContext.getSession();int oldDepth=com.tianji.common.utils.UserContext.getCallDepth();
        try {
            com.tianji.common.utils.UserContext.setUser(user);com.tianji.common.utils.UserContext.setRole(2L);
            com.tianji.common.utils.UserContext.setSession(null);com.tianji.common.utils.UserContext.setCallDepth(0);
            Long current=learning.isLessonValid(((Number)paper.get("course_id")).longValue());
            if(current==null || current.longValue()!=lesson)throw new ForbiddenException("课程权益已失效，无法继续考试");
        } finally {
            com.tianji.common.utils.UserContext.removeUser();
            if(oldUser!=null)com.tianji.common.utils.UserContext.setUser(oldUser);if(oldRole!=null)com.tianji.common.utils.UserContext.setRole(oldRole);
            if(oldSession!=null)com.tianji.common.utils.UserContext.setSession(oldSession);if(oldDepth>0)com.tianji.common.utils.UserContext.setCallDepth(oldDepth);
        }
    }
    public Map<String,Object> paper(long id) {
        var rows=jdbc.queryForList("SELECT * FROM exam_paper WHERE id=?",id);
        if(rows.isEmpty()) throw new BadRequestException("试卷不存在");return rows.getFirst();
    }
    public Map<String,Object> view(long id,long user,boolean grader) {
        var rows=jdbc.queryForList("SELECT * FROM exam_attempt WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new BadRequestException("考试记录不存在");
        var attempt=rows.getFirst();
        if(!grader && ((Number)attempt.get("user_id")).longValue()!=user) throw new BadRequestException("考试记录不存在");
        List<Map<String,Object>> questions=jdbc.queryForList("SELECT question_id,position,name,type,score,options FROM exam_paper_question WHERE paper_id=? ORDER BY position",attempt.get("paper_id"));
        for(var q:questions) {if(q.get("options")!=null) q.put("options",json.readValue(q.get("options").toString(),Object.class));}
        attempt.put("questions",questions);
        if(!"IN_PROGRESS".equals(attempt.get("status"))) attempt.put("answers",jdbc.queryForList("SELECT question_id,response,score,feedback,version FROM exam_answer WHERE attempt_id=?",id));
        return attempt;
    }
    public static int scoreObjective(String response,String correct,int maxScore) {
        Set<String> provided=answerSet(response),expected=answerSet(correct);
        return !provided.isEmpty() && provided.equals(expected)?maxScore:0;
    }
    private static Set<String> answerSet(String value) {
        if(value==null || value.isBlank()) return Set.of();
        Set<String> values=new HashSet<>();
        for(String token:value.split(",")) {String normalized=token.trim();if(!normalized.matches("[0-9]{1,2}") || !values.add(normalized)) return Set.of();}
        return values;
    }
}
