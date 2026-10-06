package com.tianji.learning.controller;
import com.tianji.api.client.course.CourseClient;
import com.tianji.common.autoconfigure.reliability.OperationStore;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.*;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.service.impl.NoteOperationHandler;
import com.tianji.learning.service.impl.NoteOperationHandler.Mutation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v2")
public class LearningFeaturesController {
    private final JdbcTemplate jdbc;private final CourseClient courses;private final OperationStore operations;
    @PutMapping("/favorites/{courseId}") public void favorite(@PathVariable long courseId) {
        long user=UserContext.requireUser();
        var course=courses.getCourseInfoById(courseId,false,false);
        if(course==null) throw new BadRequestException("课程不存在");
        jdbc.update("INSERT IGNORE INTO course_favorite(user_id,course_id) VALUES(?,?)",user,courseId);
    }
    @DeleteMapping("/favorites/{courseId}") public void unfavorite(@PathVariable long courseId) {
        jdbc.update("DELETE FROM course_favorite WHERE user_id=? AND course_id=?",UserContext.requireUser(),courseId);
    }
    @GetMapping("/favorites/{courseId}") public Map<String,Boolean> favoriteState(@PathVariable long courseId) {
        return Map.of("favorited",!jdbc.queryForList("SELECT course_id FROM course_favorite WHERE user_id=? AND course_id=?",UserContext.requireUser(),courseId).isEmpty());
    }
    @GetMapping("/favorites") public Map<String,Object> favorites(PageQuery page) {
        page.validate();
        long user=UserContext.requireUser();
        var rows=jdbc.queryForList("SELECT course_id,created_at FROM course_favorite WHERE user_id=? ORDER BY created_at DESC,course_id DESC LIMIT ? OFFSET ?",user,page.getPageSize(),page.from());
        return Map.of("list",rows,"total",jdbc.queryForObject("SELECT COUNT(*) FROM course_favorite WHERE user_id=?",Long.class,user),"pageNo",page.getPageNo());
    }
    @GetMapping("/notes") public Map<String,Object> notes(PageQuery page,@RequestParam(required=false) Long courseId) {
        page.validate();long user=UserContext.requireUser();
        String condition="user_id=? AND deleted=0"+(courseId==null?"":" AND course_id=?");
        List<Object> params=new ArrayList<>(List.of(user));if(courseId!=null) params.add(courseId);
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM course_note WHERE "+condition,Long.class,params.toArray());
        params.add(page.getPageSize());params.add(page.from());
        return Map.of("list",jdbc.queryForList("SELECT * FROM course_note WHERE "+condition+" ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?",params.toArray()),"total",count);
    }
    @GetMapping("/notes/{id}") public Map<String,Object> note(@PathVariable long id) {
        var rows=jdbc.queryForList("SELECT * FROM course_note WHERE id=? AND user_id=? AND deleted=0",id,UserContext.requireUser());
        if(rows.isEmpty()) throw new BadRequestException("笔记不存在");return rows.getFirst();
    }
    @PostMapping("/notes") public ResponseEntity<OperationStore.View> create(@RequestBody Mutation form,@RequestHeader("Idempotency-Key") String key) {
        UserContext.requireUser();NoteOperationHandler.validate(form);
        if(form.sectionId()!=null) {
            var section=courses.sectionInfo(form.sectionId());
            if(section==null || !Objects.equals(section.getCourseId(),form.courseId()) || form.moment()!=null && (section.getMediaDuration()==null || form.moment()>section.getMediaDuration()))
                throw new BadRequestException("小节或视频时间点无效");
        }
        return submit(key,new Mutation("CREATE",null,form.courseId(),form.sectionId(),form.moment(),form.content(),null));
    }
    @PutMapping("/notes/{id}") public ResponseEntity<OperationStore.View> update(@PathVariable long id,@RequestBody Mutation form,@RequestHeader("Idempotency-Key") String key) {
        return submit(key,new Mutation("UPDATE",id,null,null,null,form.content(),form.version()));
    }
    @DeleteMapping("/notes/{id}") public ResponseEntity<OperationStore.View> delete(@PathVariable long id,@RequestParam long version,@RequestHeader("Idempotency-Key") String key) {
        return submit(key,new Mutation("DELETE",id,null,null,null,null,version));
    }
    private ResponseEntity<OperationStore.View> submit(String key,Mutation form) {
        return ResponseEntity.accepted().body(operations.submit(UserContext.requireUser(),"NOTE_WRITE",key,form));
    }
}
