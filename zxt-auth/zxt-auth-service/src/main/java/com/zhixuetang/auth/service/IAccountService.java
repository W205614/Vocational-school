package com.zhixuetang.auth.service;

import com.zhixuetang.api.dto.user.LoginFormDTO;

/**
 * <p>
 * 账号表，平台内所有用户的账号、密码信息 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-06-16
 */
public interface IAccountService {

    String login(LoginFormDTO loginFormDTO, boolean isStaff);

    void completeInitialPassword(com.zhixuetang.api.dto.user.InitialPasswordDTO form);

    void logout();

    String refreshToken(String refreshToken);
}
