package com.tianji.exam.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.common.autoconfigure.reliability.OperationHandler;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

/** Revision is independent of exam grading and remains available after enrollment expires. */
@Service @RequiredArgsConstructor
public class ExamReviewService implements OperationHandler {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    public record Command(String action,long attemptId,long questionId,String response,Boolean mastered,Long version) {}
    private static final String FROM=" FROM exam_attempt a JOIN exam_paper p ON p.id=a.paper_id JOIN exam_answer r ON r.attempt_id=a.id JOIN exam_paper_question q ON q.paper_id=a.paper_id AND q.question_id=r.question_id LEFT JOIN exam_review_state s ON s.attempt_id=a.id AND s.question_id=q.question_id WHERE a.user_id=? AND a.status='FINISHED' AND r.score IS NOT NULL AND r.score<q.score";
    private static final String FIELDS="a.id AS attempt_id,p.course_id,p.section_id,q.question_id,q.name,q.type,q.options,q.answer,q.analysis,q.score AS max_score,r.response,r.score,r.feedback,a.graded_at,COALESCE(s.mastered,0) AS mastered,COALESCE(s.version,0) AS version,s.last_reviewed_at";
    @Override public String kind(){return "EXAM_REVIEW";}

    public Map<String,Object> page(long user,int pageNo,int pageSize,Long courseId,Integer type,Boolean mastered) {
        if(pageNo<1 || pageNo>10000 || pageSize<1 || pageSize>100 || courseId!=null && courseId<=0 || type!=null && (type<1 || type>5))throw new BadRequestException("复习筛选参数无效");
        String from=FROM;List<Object> args=new ArrayList<>();args.add(user);
        if(courseId!=null){from+=" AND p.course_id=?";args.add(courseId);}
        if(type!=null){from+=" AND q.type=?";args.add(type);}
        if(mastered!=null){from+=" AND COALESCE(s.mastered,0)=?";args.add(mastered?1:0);}
        Long total=jdbc.queryForObject("SELECT COUNT(*)"+from,Long.class,args.toArray());
        args.add(pageSize);args.add((pageNo-1)*pageSize);
        var list=jdbc.queryForList("SELECT "+FIELDS+from+" ORDER BY a.graded_at DESC,a.id DESC,q.position LIMIT ? OFFSET ?",args.toArray());
        list.forEach(this::decodeOptions);
        return Map.of("total",total,"pageNo",pageNo,"pageSize",pageSize,"list",list);
    }
    public Map<String,Object> detail(long user,long attemptId,long questionId) {
        var row=ownedMistake(user,attemptId,questionId);decodeOptions(row);
        row.put("reviews",jdbc.queryForList("SELECT id,response,correct,created_at FROM exam_review_record WHERE user_id=? AND attempt_id=? AND question_id=? ORDER BY created_at DESC,id DESC LIMIT 20",user,attemptId,questionId));
        return row;
    }
    @Override public Object execute(String operation,long user,String payload) {
        Command cmd=json.readValue(payload,Command.class);
        if(cmd==null || cmd.version()==null || cmd.version()<0 || !Set.of("REVIEW","STATE").contains(cmd.action()==null?"":cmd.action()))throw new BadRequestException("复习操作参数无效");
        var mistake=ownedMistake(user,cmd.attemptId(),cmd.questionId());
        if("REVIEW".equals(cmd.action()) && (cmd.response()==null || cmd.response().isBlank() || cmd.response().length()>10000))throw new BadRequestException("复习答案不能为空或超过10000字");
        if("STATE".equals(cmd.action()) && cmd.mastered()==null)throw new BadRequestException("掌握状态不能为空");
        jdbc.update("INSERT INTO exam_review_state(attempt_id,question_id,user_id) VALUES(?,?,?) ON DUPLICATE KEY UPDATE user_id=user_id",cmd.attemptId(),cmd.questionId(),user);
        var state=jdbc.queryForMap("SELECT version FROM exam_review_state WHERE attempt_id=? AND question_id=? AND user_id=? FOR UPDATE",cmd.attemptId(),cmd.questionId(),user);
        if(((Number)state.get("version")).longValue()!=cmd.version())throw new ConflictException("复习记录已在其他页面更新，请刷新后重试");
        if("REVIEW".equals(cmd.action())) {
            int type=((Number)mistake.get("type")).intValue();
            // Subjective answers require self review; no fabricated automatic grade.
            Boolean correct=type==5?null:ExamWorkflowService.scoreObjective(cmd.response(),Objects.toString(mistake.get("answer"),""),1)==1;
            jdbc.update("INSERT INTO exam_review_record(id,operation_id,attempt_id,question_id,user_id,response,correct) VALUES(?,?,?,?,?,?,?)",IdWorker.getId(),operation,cmd.attemptId(),cmd.questionId(),user,cmd.response(),correct);
            jdbc.update("UPDATE exam_review_state SET version=version+1,last_reviewed_at=CURRENT_TIMESTAMP(3),updated_at=CURRENT_TIMESTAMP(3) WHERE attempt_id=? AND question_id=?",cmd.attemptId(),cmd.questionId());
        } else jdbc.update("UPDATE exam_review_state SET mastered=?,version=version+1,updated_at=CURRENT_TIMESTAMP(3) WHERE attempt_id=? AND question_id=?",cmd.mastered()?1:0,cmd.attemptId(),cmd.questionId());
        return detail(user,cmd.attemptId(),cmd.questionId());
    }
    private Map<String,Object> ownedMistake(long user,long attemptId,long questionId) {
        if(attemptId<=0 || questionId<=0)throw new BadRequestException("复习题目标识无效");
        var rows=jdbc.queryForList("SELECT "+FIELDS+FROM+" AND a.id=? AND q.question_id=?",user,attemptId,questionId);
        if(rows.isEmpty())throw new BadRequestException("已评分错题不存在");return rows.getFirst();
    }
    private void decodeOptions(Map<String,Object> row){if(row.get("options")!=null)row.put("options",json.readValue(row.get("options").toString(),Object.class));}
}
