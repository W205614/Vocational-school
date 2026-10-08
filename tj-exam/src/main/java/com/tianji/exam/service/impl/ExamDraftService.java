package com.tianji.exam.service.impl;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

@Service @RequiredArgsConstructor
public class ExamDraftService {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final ExamWorkflowService workflow;
    public record Save(Long version, Map<Long,String> answers) {}
    public record View(long version, Map<String,String> answers, String status) {}

    @Transactional public View read(long id, long user) {
        var attempt=ownedAttempt(id,user);
        return readLocked(id,attempt.get("status").toString());
    }

    @Transactional public View save(long id, long user, Save request) {
        // Validate the durable owner before taking the attempt lock for a write.
        // Reading retained history remains independent of current entitlement.
        workflow.requireDraftWrite(id,user);
        var attempt=ownedAttempt(id,user);
        if(!"IN_PROGRESS".equals(attempt.get("status"))) throw new ConflictException("答卷已提交，不能修改草稿");
        if(request==null || request.version()==null || request.version()<0 || request.answers()==null || request.answers().size()>100)
            throw new BadRequestException("草稿参数无效");
        Set<Long> valid=new HashSet<>();
        jdbc.queryForList("SELECT question_id FROM exam_paper_question WHERE paper_id=?",attempt.get("paper_id"))
                .forEach(row->valid.add(((Number)row.get("question_id")).longValue()));
        if(!valid.containsAll(request.answers().keySet())) throw new BadRequestException("草稿包含试卷外题目");
        Map<String,String> canonical=new TreeMap<>();
        request.answers().forEach((key,value)->{
            if(value==null || value.length()>10000) throw new BadRequestException("答案无效或过长");
            canonical.put(key.toString(),value);
        });
        View current=readLocked(id,"IN_PROGRESS");
        // A lost response can be retried without creating another draft revision.
        if(current.answers().equals(canonical) && (request.version()==current.version() || request.version()==current.version()-1)) return current;
        if(request.version()!=current.version()) throw new ConflictException("草稿已在其他页面更新，请载入最新草稿后继续");
        long next=Math.addExact(current.version(),1);
        jdbc.update("INSERT INTO exam_draft(attempt_id,answers,version) VALUES(?,?,?) ON DUPLICATE KEY UPDATE answers=VALUES(answers),version=VALUES(version),updated_at=CURRENT_TIMESTAMP(3)",
                id,json.writeValueAsString(canonical),next);
        return new View(next,canonical,"IN_PROGRESS");
    }
    private Map<String,Object> ownedAttempt(long id,long user) {
        // Shares the attempt lock with submission, so late saves cannot alter a submitted answer.
        var rows=jdbc.queryForList("SELECT user_id,paper_id,status FROM exam_attempt WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty() || ((Number)rows.getFirst().get("user_id")).longValue()!=user) throw new BadRequestException("考试记录不存在");
        return rows.getFirst();
    }
    @SuppressWarnings("unchecked")
    private View readLocked(long id,String status) {
        // Entitlement validation can establish an earlier REPEATABLE READ
        // snapshot. After waiting for the attempt lock, read the latest revision.
        var rows=jdbc.queryForList("SELECT answers,version FROM exam_draft WHERE attempt_id=? FOR UPDATE",id);
        if(rows.isEmpty()) return new View(0,Map.of(),status);
        var row=rows.getFirst();
        return new View(((Number)row.get("version")).longValue(),json.readValue(row.get("answers").toString(),Map.class),status);
    }
}
