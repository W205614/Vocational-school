package com.tianji.learning.controller;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.service.impl.LearningHubOperationHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v2/teacher/learning-hub")
public class TeacherLearningHubController {
    private final JdbcTemplate jdbc;private final CourseClient courses;private final UserClient users;private final LearningHubOperationHandler permissions;
    public TeacherLearningHubController(JdbcTemplate jdbc,CourseClient courses,UserClient users,LearningHubOperationHandler permissions){this.jdbc=jdbc;this.courses=courses;this.users=users;this.permissions=permissions;}
    @GetMapping public Object overview(@RequestParam long courseId,@RequestParam(required=false) Long userId,PageQuery page) {
        page.validate();permissions.requireTeacher(UserContext.requireUser(),UserContext.getRole(),courseId);
        var course=courses.getCourseInfoById(courseId,false,false);int sections=course==null || course.getSectionNum()==null?0:course.getSectionNum();
        var stats=jdbc.queryForMap("SELECT COUNT(*) AS students,COALESCE(SUM(status=2),0) AS completedStudents,COALESCE(SUM(learned_sections),0) AS learnedSections FROM learning_lesson WHERE course_id=?",courseId);
        long students=((Number)stats.get("students")).longValue();
        var summary=new LinkedHashMap<String,Object>();summary.put("students",students);summary.put("completedStudents",stats.get("completedStudents"));summary.put("completionRate",students==0?0:Math.round(((Number)stats.get("completedStudents")).doubleValue()*1000/students)/10.0);
        summary.put("unansweredQuestions",jdbc.queryForObject("SELECT COUNT(*) FROM interaction_question WHERE course_id=? AND hidden=0 AND answer_times=0",Long.class,courseId));
        summary.put("unacceptedQuestions",jdbc.queryForObject("SELECT COUNT(*) FROM interaction_question WHERE course_id=? AND hidden=0 AND accepted_reply_id IS NULL",Long.class,courseId));
        String condition="course_id=?"+(userId==null?"":" AND user_id=?");List<Object> args=new ArrayList<>(List.of(courseId));if(userId!=null)args.add(userId);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM learning_lesson WHERE "+condition,Long.class,args.toArray());args.add(page.getPageSize());args.add(page.from());
        var rows=jdbc.queryForList("SELECT id AS lessonId,CAST(user_id AS CHAR) AS userId,learned_sections AS learnedSections,status,latest_learn_time AS latestLearnTime,expire_time AS expireTime FROM learning_lesson WHERE "+condition+" ORDER BY latest_learn_time DESC,id DESC LIMIT ? OFFSET ?",args.toArray());
        var times=new HashMap<Long,Map<String,Object>>();
        if(!rows.isEmpty()) {
            var ids=rows.stream().map(row->((Number)row.get("lessonId")).longValue()).toList();
            String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
            for(var time:jdbc.queryForList("SELECT lesson_id,SUM(watched_millis) AS watchedMillis,SUM(credited_millis) AS creditedMillis FROM learning_record WHERE lesson_id IN ("+placeholders+") GROUP BY lesson_id",ids.toArray()))
                times.put(((Number)time.get("lesson_id")).longValue(),time);
        }
        for(var row:rows){var time=times.get(((Number)row.remove("lessonId")).longValue());row.put("watchedMillis",time==null?0:time.get("watchedMillis"));row.put("creditedMillis",time==null?0:time.get("creditedMillis"));}
        var names=new HashMap<Long,String>();if(!rows.isEmpty()){var details=users.queryUserByIds(rows.stream().map(r->Long.parseLong(r.get("userId").toString())).toList());if(details!=null)details.forEach(u->names.put(u.getId(),u.getName()));}
        for(var row:rows){long student=Long.parseLong(row.get("userId").toString());row.put("name",names.getOrDefault(student,"同学 "+student));row.put("sections",sections);row.put("progressPercent",sections==0?0:Math.min(100,Math.round(((Number)row.get("learnedSections")).doubleValue()*100/sections)));}
        return Map.of("courseId",Long.toString(courseId),"courseName",course==null?"课程":Objects.requireNonNullElse(course.getName(),"课程"),"summary",summary,"list",rows,"total",total);
    }
}
