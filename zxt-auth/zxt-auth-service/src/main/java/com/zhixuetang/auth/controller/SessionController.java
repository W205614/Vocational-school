package com.zhixuetang.auth.controller;

import com.zhixuetang.auth.service.impl.SessionStore;
import com.zhixuetang.api.client.user.UserClient;
import com.zhixuetang.common.domain.dto.LoginUserDTO;
import com.zhixuetang.common.utils.*;
import com.zhixuetang.common.exceptions.UnauthorizedException;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class SessionController {
    private final SessionStore sessions;
    private final UserClient users;

    @GetMapping("/accounts/sessions")
    public Object list() {
        return sessions.list(UserContext.requireUser());
    }

    @DeleteMapping("/accounts/sessions/{id}")
    public void revoke(@PathVariable String id) {
        sessions.revoke(id, UserContext.requireUser());
    }

    @GetMapping("/internal/v2/sessions/{id}/validate")
    public com.zhixuetang.common.domain.R<?> validate(
            @PathVariable String id,
            @RequestParam long user,
            @RequestParam long role,
            @RequestParam long version) {
        InternalAuth.requireService();
        var current = users.sessionIdentity(user);
        if (current == null
                || !Objects.equals(current.getRoleId(), role)
                || !Objects.equals(current.getAuthVersion(), version))
            throw new UnauthorizedException("账号身份已变化");
        current.setSessionId(id);
        sessions.validate(current);
        return com.zhixuetang.common.domain.R.ok();
    }
}
