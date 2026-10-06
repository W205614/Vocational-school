package com.tianji.learning.service.impl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.common.autoconfigure.reliability.OperationHandler;
import com.tianji.common.exceptions.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
@Service @RequiredArgsConstructor
public class NoteOperationHandler implements OperationHandler {
    private final JdbcTemplate jdbc; private final JsonMapper json;
    public record Mutation(String action,Long id,Long courseId,Long sectionId,Integer moment,String content,Long version) {}
    @Override public String kind() {return "NOTE_WRITE";}
    @Override public Object execute(String operation,long user,String payload) {
        Mutation form=json.readValue(payload,Mutation.class);
        if("CREATE".equals(form.action())) {
            validate(form);
            if(jdbc.queryForList("SELECT id FROM learning_lesson WHERE user_id=? AND course_id=? AND status<>3 AND (expire_time IS NULL OR expire_time>CURRENT_TIMESTAMP(3))",user,form.courseId()).isEmpty()) throw new BadRequestException("无课程学习权限");
            long id=IdWorker.getId();
            jdbc.update("INSERT INTO course_note(id,user_id,course_id,section_id,moment,content) VALUES(?,?,?,?,?,?)",id,user,form.courseId(),form.sectionId(),form.moment(),form.content());
            return jdbc.queryForMap("SELECT * FROM course_note WHERE id=?",id);
        }
        if(form.id()==null || form.version()==null) throw new BadRequestException("笔记标识和版本不能为空");
        if("UPDATE".equals(form.action())) {
            if(form.content()==null || form.content().isBlank() || form.content().length()>10000) throw new BadRequestException("笔记内容无效");
            if(jdbc.update("UPDATE course_note SET content=?,version=version+1 WHERE id=? AND user_id=? AND version=? AND deleted=0",form.content(),form.id(),user,form.version())!=1) throw new ConflictException("笔记已删除或被其他编辑更新");
            return jdbc.queryForMap("SELECT * FROM course_note WHERE id=? AND user_id=?",form.id(),user);
        }
        if(!"DELETE".equals(form.action())) throw new BadRequestException("笔记操作无效");
        if(jdbc.update("UPDATE course_note SET deleted=1,version=version+1 WHERE id=? AND user_id=? AND version=? AND deleted=0",form.id(),user,form.version())!=1) throw new ConflictException("笔记已删除或版本已变化");
        return Map.of("id",form.id(),"deleted",true);
    }
    public static void validate(Mutation form) {
        if(form.courseId()==null || form.content()==null || form.content().isBlank() || form.content().length()>10000 || form.moment()!=null && form.moment()<0)
            throw new BadRequestException("笔记参数无效");
        if(form.moment()!=null && form.sectionId()==null) throw new BadRequestException("时间点必须关联小节");
    }
}
