package com.zhixuetang.user.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zhixuetang.api.dto.user.LoginFormDTO;
import com.zhixuetang.api.dto.user.UserDTO;
import com.zhixuetang.common.domain.dto.LoginUserDTO;
import com.zhixuetang.user.domain.dto.UserFormDTO;
import com.zhixuetang.user.domain.po.User;
import com.zhixuetang.user.domain.vo.UserDetailVO;

/**
 * <p>
 * 学员用户表 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-06-28
 */
public interface IUserService extends IService<User> {
    LoginUserDTO queryUserDetail(LoginFormDTO loginDTO, boolean isStaff);

    com.zhixuetang.api.dto.user.TemporaryCredentialDTO resetPassword(Long userId);

    UserDetailVO myInfo();

    void addUserByPhone(User user, String code);

    com.zhixuetang.api.dto.user.TemporaryCredentialDTO saveUser(UserDTO userDTO);

    void updateUser(UserDTO userDTO);

    void updateUserWithPassword(UserFormDTO userDTO);
}
