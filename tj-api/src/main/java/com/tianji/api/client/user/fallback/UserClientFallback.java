package com.tianji.api.client.user.fallback;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.LoginFormDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.LoginUserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.Collections;
import java.util.List;

@Slf4j
public class UserClientFallback implements FallbackFactory<UserClient> {
    @Override
    public UserClient create(Throwable cause) {
        log.error("查询用户服务出现异常", cause);
        return new UserClient() {
            @Override public LoginUserDTO sessionIdentity(long id){if(cause instanceof feign.FeignException e && (e.status()==401 || e.status()==403))throw new com.tianji.common.exceptions.UnauthorizedException("账号身份已失效");if(cause instanceof com.tianji.common.exceptions.UnauthorizedException e)throw e;throw new com.tianji.common.exceptions.ServiceUnavailableException("身份服务暂不可用");}

            @Override
            public Long exchangeUserIdWithPhone(String phone) {
                return null;
            }

            @Override
            public LoginUserDTO queryUserDetail(LoginFormDTO loginDTO, boolean isStaff) {
                if(cause instanceof com.tianji.common.exceptions.BadRequestException e)throw e;
                if(cause instanceof feign.FeignException e && e.status()>=400 && e.status()<500)throw new com.tianji.common.exceptions.BadRequestException("登录信息有误");
                throw new com.tianji.common.exceptions.ServiceUnavailableException("身份服务暂不可用");
            }

            @Override
            public Integer queryUserType(Long id) {
                return null;
            }

            @Override
            public List<UserDTO> queryUserByIds(Iterable<Long> ids) {
                return Collections.emptyList();
            }

            @Override
            public UserDTO queryUserById(Long id) {
                return null;
            }
        };
    }
}
