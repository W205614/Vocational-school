package com.tianji.auth.service.impl;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.LoginFormDTO;
import com.tianji.auth.common.constants.JwtConstants;
import com.tianji.auth.service.IAccountService;
import com.tianji.auth.service.ILoginRecordService;
import com.tianji.auth.util.JwtTool;
import com.tianji.common.domain.dto.LoginUserDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.WebUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 账号表，平台内所有用户的账号、密码信息 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-06-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements IAccountService{
    private final LoginProtection protection;
    private final SessionStore sessions;
    private final JwtTool jwtTool;
    private final UserClient userClient;
    private final ILoginRecordService loginRecordService;

    @Override
    public String login(LoginFormDTO loginDTO, boolean isStaff) {
        // 1.查询并校验用户信息
        String account=(Integer.valueOf(2).equals(loginDTO.getType())?java.util.Objects.toString(loginDTO.getCellPhone(),""):java.util.Objects.toString(loginDTO.getUsername(),"")).trim().toLowerCase(java.util.Locale.ROOT);
        String ip=java.util.Objects.toString(WebUtils.getHeader("X-Client-IP"),"unknown");protection.before(account,ip);
        LoginUserDTO detail;
        try{detail=userClient.queryUserDetail(loginDTO,isStaff);if(detail==null)throw new BadRequestException("登录信息有误");}
        catch(BadRequestException error){protection.failed(account,ip);throw new BadRequestException("登录信息有误");}
        catch(feign.FeignException error){if(error.status()==400 || error.status()==401){protection.failed(account,ip);throw new BadRequestException("登录信息有误");}throw new com.tianji.common.exceptions.ServiceUnavailableException("身份服务暂不可用");}
        LoginUserDTO current=userClient.sessionIdentity(detail.getUserId());if(isStaff==Long.valueOf(2).equals(current.getRoleId()))throw new BadRequestException("登录入口与账号身份不匹配");detail.setAuthVersion(current.getAuthVersion());detail.setRoleId(current.getRoleId());protection.success(account);
        if (detail == null) {
            throw new BadRequestException("登录信息有误");
        }

        // 2.基于JWT生成登录token
        // 2.1.设置记住我标记
        detail.setRememberMe(loginDTO.getRememberMe());
        // 2.2.生成token
        String token = generateToken(detail);

        // 3.计入登录信息表
        loginRecordService.loginSuccess(loginDTO.getCellPhone(), detail.getUserId());
        // 4.返回结果
        return token;
    }

    private String generateToken(LoginUserDTO detail) {
        // 2.2.生成access-token
        String token;
        // 2.3.生成refresh-token，在数据库轮换独立会话的刷新标识
        String refreshToken = jwtTool.createRefreshToken(detail);
        token = jwtTool.createToken(detail);
        // 2.4.将refresh-token写入用户cookie，并设置HttpOnly为true
        int maxAge = BooleanUtils.isTrue(detail.getRememberMe()) ?
                (int) JwtConstants.JWT_REMEMBER_ME_TTL.toSeconds() : -1;
        WebUtils.cookieBuilder()
                .name(detail.getRoleId() == 2 ? JwtConstants.REFRESH_HEADER : JwtConstants.ADMIN_REFRESH_HEADER)
                .value(refreshToken)
                .maxAge(maxAge)
                .httpOnly(true)
                .build();
        return token;
    }

    @Override
    public void logout() {
        // 删除jti
        jwtTool.cleanJtiCache();
        // 删除cookie
        WebUtils.cookieBuilder()
                .name(JwtConstants.REFRESH_HEADER)
                .value("")
                .maxAge(0)
                .httpOnly(true)
                .build();
        WebUtils.cookieBuilder().name(JwtConstants.ADMIN_REFRESH_HEADER)
                .value("").maxAge(0).httpOnly(true).build();
    }

    @Override
    public String refreshToken(String refreshToken) {
        // 1.校验refresh-token,校验JTI
        LoginUserDTO userDTO = jwtTool.parseRefreshToken(refreshToken);
        // 2.生成新的access-token、refresh-token
        LoginUserDTO current=userClient.sessionIdentity(userDTO.getUserId());
        if(current==null || !java.util.Objects.equals(current.getAuthVersion(),userDTO.getAuthVersion()) || !java.util.Objects.equals(current.getRoleId(),userDTO.getRoleId())){sessions.revoke(userDTO.getSessionId(),userDTO.getUserId());throw new com.tianji.common.exceptions.UnauthorizedException("账号身份已变化，请重新登录");}
        return generateToken(userDTO);
    }
}
