package com.tianji.course.controller;
import com.tianji.common.utils.UserContext;
import com.tianji.common.exceptions.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v2/teacher")
public class TeacherCoursesController {
 private final JdbcTemplate jdbc;
 @GetMapping("/courses") public Object courses(@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="50") int pageSize) {
  long user=UserContext.requireUser();Long role=UserContext.getRole();
  if(!Set.of(1L,3L).contains(role))throw new ForbiddenException("需要教师权限");
  if(pageNo<1 || pageNo>10000 || pageSize<1 || pageSize>100)throw new com.tianji.common.exceptions.BadRequestException("分页无效");
  String where=" WHERE c.deleted=0"+(Long.valueOf(1).equals(role)?"":" AND EXISTS(SELECT 1 FROM course_teacher t WHERE t.course_id=c.id AND t.teacher_id=? AND t.deleted=0)");
  List<Object> args=new ArrayList<>();if(!Long.valueOf(1).equals(role))args.add(user);
  long total=jdbc.queryForObject("SELECT COUNT(*) FROM course c"+where,Long.class,args.toArray());
  args.add(pageSize);args.add((long)(pageNo-1)*pageSize);
  return Map.of("total",total,"list",jdbc.queryForList("SELECT CAST(c.id AS CHAR) AS id,c.name FROM course c"+where+" ORDER BY c.name,c.id LIMIT ? OFFSET ?",args.toArray()));
 }
}
