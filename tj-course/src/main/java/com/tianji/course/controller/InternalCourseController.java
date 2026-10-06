package com.tianji.course.controller;
import com.tianji.course.domain.dto.CourseSimpleInfoListDTO;
import com.tianji.course.service.ICourseService;
import com.tianji.common.utils.InternalAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Hidden;
@RestController @RequiredArgsConstructor @Hidden @RequestMapping("/internal/v1/courses")
public class InternalCourseController {
 private final ICourseService courses;
 @GetMapping("/simpleInfo/list") public Object simple(CourseSimpleInfoListDTO request){
  InternalAuth.requireService();if(request.getIds()==null || request.getIds().isEmpty() || request.getIds().size()>100)throw new com.tianji.common.exceptions.BadRequestException("每次查询 1 到 100 门课程");
  return courses.getSimpleInfoList(request);
 }
}
