package com.zhixuetang.user.controller;

import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.user.domain.dto.StudentFormDTO;
import com.zhixuetang.user.domain.query.UserPageQuery;
import com.zhixuetang.user.domain.vo.StudentPageVo;
import com.zhixuetang.user.service.IStudentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学员详情表 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2022-07-12
 */
@RestController
@RequestMapping("/students")
@Tag(name = "用户管理接口")
public class StudentController {

    @Autowired private IStudentService studentService;

    @Operation(summary = "分页查询学生信息")
    @GetMapping("/page")
    public PageDTO<StudentPageVo> queryStudentPage(UserPageQuery pageQuery) {
        return studentService.queryStudentPage(pageQuery);
    }

    @Operation(summary = "学员注册")
    @PostMapping("/register")
    public void registerStudent(@RequestBody StudentFormDTO studentFormDTO) {
        studentService.saveStudent(studentFormDTO);
    }
}
