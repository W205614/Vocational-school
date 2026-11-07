package com.zhixuetang.user.service;

import com.zhixuetang.common.domain.dto.PageDTO;
import com.zhixuetang.user.domain.dto.StudentFormDTO;
import com.zhixuetang.user.domain.query.UserPageQuery;
import com.zhixuetang.user.domain.vo.StudentPageVo;

/**
 * <p>
 * 学员详情表 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-07-12
 */
public interface IStudentService {

    void saveStudent(StudentFormDTO studentFormDTO);

    PageDTO<StudentPageVo> queryStudentPage(UserPageQuery pageQuery);
}
