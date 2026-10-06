package com.tianji.authsdk.gateway.util;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTValidator;
import com.tianji.auth.common.domain.PrivilegeRoleDTO;
import com.tianji.common.domain.R;
import com.tianji.common.domain.dto.LoginUserDTO;
import com.tianji.common.exceptions.ForbiddenException;
import com.tianji.common.exceptions.UnauthorizedException;
import com.tianji.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.AntPathMatcher;

import java.util.*;
import java.util.stream.Collectors;

import static com.tianji.auth.common.constants.AuthErrorInfo.Code.EXPIRED_TOKEN_CODE;
import static com.tianji.auth.common.constants.AuthErrorInfo.Code.INVALID_TOKEN_CODE;
import static com.tianji.auth.common.constants.AuthErrorInfo.Msg.*;
import static com.tianji.auth.common.constants.JwtConstants.*;

@Slf4j
public class AuthUtil {
    private record Snapshot(int version, Map<String,Set<Long>> permissions) {}
    private volatile Snapshot snapshot=new Snapshot(-1,Map.of());
    private volatile long refreshedAt;
    public boolean isReady(){return snapshot.version()>=0 && System.nanoTime()-refreshedAt<java.util.concurrent.TimeUnit.SECONDS.toNanos(60);}

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();
    private final JwtSignerHolder jwtSignerHolder;
    private final StringRedisTemplate stringRedisTemplate;
    private final BoundHashOperations<String, String, String> hashOps;

    public AuthUtil(JwtSignerHolder jwtSignerHolder, StringRedisTemplate stringRedisTemplate) {
        this.jwtSignerHolder = jwtSignerHolder;
        this.stringRedisTemplate = stringRedisTemplate;
        this.hashOps = stringRedisTemplate.boundHashOps(AUTH_PRIVILEGE_KEY);
    }

    public R<LoginUserDTO> parseToken(String token) {
        // 1.校验token是否为空
        if(StringUtils.isBlank(token)){
            return R.error(INVALID_TOKEN_CODE, INVALID_TOKEN);
        }
        if(jwtSignerHolder.getJwtSigner()==null)throw new com.tianji.common.exceptions.ServiceUnavailableException("鉴权密钥尚未就绪，请稍后重试");
        JWT jwt = null;
        try {
            jwt = JWT.of(token).setSigner(jwtSignerHolder.getJwtSigner());
            if(!jwt.verify())return R.error(INVALID_TOKEN_CODE,INVALID_TOKEN);
        } catch (Exception e) {
            return R.error(INVALID_TOKEN_CODE, INVALID_TOKEN);
        }
        // 2.校验jwt是否有效
        // 3.校验是否过期
        try {
            JWTValidator.of(jwt).validateDate();
        } catch (ValidateException e) {
            return R.error(EXPIRED_TOKEN_CODE, EXPIRED_TOKEN);
        }
        // 4.数据格式校验
        Object userPayload = jwt.getPayload(PAYLOAD_USER_KEY);
        if (userPayload == null) {
            // 数据为空
            return R.error(INVALID_TOKEN_CODE, INVALID_TOKEN_PAYLOAD);
        }

        // 5.数据解析
        LoginUserDTO userDTO;
        try {
            userDTO = ((JSONObject)userPayload).toBean(LoginUserDTO.class);
        } catch (RuntimeException e) {
            // token格式有误
            return R.error(INVALID_TOKEN_CODE, INVALID_TOKEN_PAYLOAD);
        }

        // 6.返回
        return R.ok(userDTO);
    }

    public void checkAuth(String antPath, R<LoginUserDTO> r) {
        Snapshot current=snapshot;
        if(!isReady()) throw new com.tianji.common.exceptions.ServiceUnavailableException("权限配置尚未就绪或已过期");
        String match=current.permissions().keySet().stream()
                .filter(p->antPathMatcher.match(p,antPath))
                .min(antPathMatcher.getPatternComparator(antPath)).orElse(null);
        if(match==null){if(!r.success())throw new UnauthorizedException(r.getCode(),r.getMsg());return;}
        if(!r.success()) throw new UnauthorizedException(r.getCode(),r.getMsg());
        if(!current.permissions().get(match).contains(r.getData().getRoleId()))
            throw new ForbiddenException(FORBIDDEN);
    }

    private List<PrivilegeRoleDTO> loadPrivileges(){
        List<String> values = hashOps.values();
        if(CollUtil.isEmpty(values)){
            return Collections.emptyList();
        }
        return values.stream()
                .map(json -> JSONUtil.toBean(json, PrivilegeRoleDTO.class))
                .collect(Collectors.toList());
    }

    private int currentVersion() {
        String version = stringRedisTemplate.opsForValue().get(AUTH_PRIVILEGE_VERSION_KEY);
        if(StrUtil.isEmpty(version)){
            return 0;
        }
        return Integer.parseInt(version);
    }


    @Scheduled(fixedDelay = 20000)
    public void refreshTask() {
        int version=currentVersion();
        if(version==snapshot.version()){refreshedAt=System.nanoTime();return;}
        List<PrivilegeRoleDTO> loaded=loadPrivileges();
        Map<String,Set<Long>> map=new HashMap<>();
        for(PrivilegeRoleDTO p:loaded) {
            if(p.getAntPath()==null || p.getRoles()==null) throw new IllegalStateException("权限配置不完整");
            map.put(p.getAntPath(),Set.copyOf(p.getRoles()));
        }
        // Do not publish a snapshot across a concurrent Redis configuration update.
        if(version!=currentVersion()) return;
        snapshot=new Snapshot(version,Map.copyOf(map));refreshedAt=System.nanoTime();
    }
}
