package com.tianji.gateway.filter;
import com.tianji.authsdk.gateway.util.AuthUtil;
import com.tianji.common.domain.R;
import com.tianji.common.domain.dto.LoginUserDTO;
import com.tianji.common.exceptions.*;
import com.tianji.gateway.config.AuthProperties;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import static com.tianji.auth.common.constants.JwtConstants.*;
@Component
public class AccountAuthFilter implements GlobalFilter,Ordered {
    private final AuthUtil auth;
    private final AuthProperties properties;
    private final com.tianji.gateway.config.AccessPolicy policy;
    private final reactor.core.scheduler.Scheduler authorizationScheduler;
    private final com.tianji.gateway.config.TrustedProxyIdentity proxyIdentity;
    private final com.tianji.gateway.config.GatewayTrafficLimits limits;
    private final java.util.concurrent.Semaphore requests;
    private final AntPathMatcher matcher=new AntPathMatcher();
    public AccountAuthFilter(AuthUtil auth,AuthProperties properties,com.tianji.gateway.config.AccessPolicy policy,reactor.core.scheduler.Scheduler authorizationScheduler,com.tianji.gateway.config.TrustedProxyIdentity proxyIdentity) {this(auth,properties,policy,authorizationScheduler,proxyIdentity,new com.tianji.gateway.config.GatewayTrafficLimits());}
    @org.springframework.beans.factory.annotation.Autowired
    public AccountAuthFilter(AuthUtil auth,AuthProperties properties,com.tianji.gateway.config.AccessPolicy policy,@org.springframework.beans.factory.annotation.Qualifier("authorizationScheduler") reactor.core.scheduler.Scheduler authorizationScheduler,com.tianji.gateway.config.TrustedProxyIdentity proxyIdentity,com.tianji.gateway.config.GatewayTrafficLimits limits) {this.auth=auth;this.properties=properties;this.policy=policy;this.authorizationScheduler=authorizationScheduler;this.proxyIdentity=proxyIdentity;this.limits=limits;this.requests=limits.apiCapacity();}
    @Override public Mono<Void> filter(ServerWebExchange exchange,GatewayFilterChain chain) {
        // Permission-cache Redis calls are blocking; never run them on Netty's event loop.
        // Acquire off Netty. using also disposes an acquisition completed after cancellation,
        // and eager cleanup releases capacity before downstream observes completion.
        boolean media=exchange.getRequest().getPath().value().matches("/api/v2/(services|admin)/media/(local-content|cloud-content)/[^/]+");
        return Mono.using(()->limits.acquire(media,proxyIdentity.identify(exchange.getRequest()).address()),permit->Mono.defer(()->authorize(exchange,chain)),com.tianji.gateway.config.GatewayTrafficLimits.Lease::close,true)
                .subscribeOn(authorizationScheduler);
    }
    private Mono<Void> authorize(ServerWebExchange exchange,GatewayFilterChain chain) {
        var client=proxyIdentity.identify(exchange.getRequest());
        // Identity is derived exclusively from the signed token. Strip on every path, including public paths.
        ServerWebExchange sanitized=exchange.mutate().request(b->b.headers(h->{
            h.remove("user-session");h.remove("X-Client-IP");h.remove("X-Client-Proto");h.remove("Forwarded");h.remove("X-Forwarded-Proto");h.remove("X-Forwarded-Host");h.remove("X-Forwarded-Port");h.remove("X-Forwarded-For");h.remove("X-Real-IP");h.remove(USER_HEADER);h.remove("user-role");h.remove("X-User-Id");h.remove("X-Role-Id");h.remove("X-Internal-Token");h.remove("X-TJ-Call-Depth");
            h.remove("X-TJ-Service");
            h.set("X-Client-IP",client.address());h.set("X-Client-Proto",client.protocol());
        })).build();
        String path=sanitized.getRequest().getPath().value();
        if(path.contains("/api/v2/services/pay/pay-orders") || path.contains("/api/v2/services/pay/refund-orders") || path.contains("/api/v2/admin/pay/pay-orders") || path.contains("/api/v2/admin/pay/refund-orders") || path.contains("/internal/") || path.contains("/user-coupons/use") || path.contains("/user-coupons/refund"))
            throw new ForbiddenException("内部接口不对外开放");
        String ant=sanitized.getRequest().getMethod().name()+":"+path;
        if(path.startsWith("/api/v2/") && !policy.declares(sanitized.getRequest().getMethod().name(),path))throw new ForbiddenException("该接口没有授权声明");
        if(ant.startsWith("GET:/api/v2/services/media/local-content/") || ant.startsWith("GET:/api/v2/services/media/cloud-content/"))return chain.filter(sanitized);
        if(java.util.Set.of("GET:/api/v2/public/accounts/privacy","POST:/api/v2/public/accounts/verification-codes","POST:/api/v2/public/accounts/registration","POST:/api/v2/public/accounts/password-recovery","POST:/api/v2/auth/accounts/mfa/enroll","POST:/api/v2/auth/accounts/mfa/confirm","POST:/api/v2/services/pay/notify/ali").contains(ant)){limits.publicQuery(client.address());return chain.filter(sanitized);}
        if(ant.equals("GET:/api/v2/public/courses") || ant.equals("GET:/api/v2/public/categories") || ant.matches("GET:/api/v2/public/courses/[0-9]{1,20}")){limits.publicQuery(client.address());return chain.filter(sanitized);}
        if(ant.matches("GET:/api/v2/services/media/course-covers/[0-9a-f]{64}\\.(png|jpg)")){limits.publicQuery(client.address());return chain.filter(sanitized);}
        if(ant.equals("GET:/api/v2/environment") || ant.equals("POST:/api/v2/auth/accounts/password/initial") || ant.equals("POST:/api/v2/auth/accounts/login") || ant.equals("POST:/api/v2/auth/accounts/admin/login") || ant.equals("GET:/api/v2/auth/accounts/refresh"))
            return chain.filter(sanitized);
        if(properties.getExcludePath().stream().anyMatch(p->matcher.match(p,ant))) return chain.filter(sanitized);
        String token=sanitized.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);
        if(token!=null && token.startsWith("Bearer ")) token=token.substring(7);
        R<LoginUserDTO> result=auth.parseToken(token);
        if(path.startsWith("/api/v2/") && !result.success()) throw new UnauthorizedException("请先登录");
        if(path.contains("/api/v2/admin/") && result.success() && !Long.valueOf(1).equals(result.getData().getRoleId()))
            throw new ForbiddenException("需要管理员权限");
        if(path.startsWith("/api/v2/services/") && result.success() && !Long.valueOf(1).equals(result.getData().getRoleId())) {
            java.util.List<String> studentPaths=java.util.List.of(
                "GET:/api/v2/services/search/courses/portal","GET:/api/v2/services/search/recommend/**",
                "GET:/api/v2/services/course/course/*","GET:/api/v2/services/course/courses/*/catalogs","GET:/api/v2/services/course/categorys/list",
                "GET:/api/v2/services/learning/lessons/**","POST:/api/v2/services/learning/lessons/plans",
                "GET:/api/v2/services/learning/learning-records/course/*","POST:/api/v2/services/learning/learning-records",
                "POST:/api/v2/services/learning/learning-records/playback-sessions","POST:/api/v2/services/learning/learning-records/playback-sessions/heartbeat","POST:/api/v2/services/learning/learning-records/playback-sessions/end",
                "GET:/api/v2/services/learning/questions/**","POST:/api/v2/services/learning/questions","PUT:/api/v2/services/learning/questions/*","DELETE:/api/v2/services/learning/questions/*",
                "GET:/api/v2/services/learning/replies/page","POST:/api/v2/services/learning/replies","GET:/api/v2/services/learning/points/today","GET:/api/v2/services/learning/boards","GET:/api/v2/services/learning/board/seasons/list",
                "GET:/api/v2/services/trade/carts","POST:/api/v2/services/trade/carts","DELETE:/api/v2/services/trade/carts/**",
                "POST:/api/v2/services/trade/refund-apply","PUT:/api/v2/services/trade/refund-apply/cancel","GET:/api/v2/services/trade/refund-apply/detail/*",
                "GET:/api/v2/services/trade/pay/channels","POST:/api/v2/services/trade/pay/order",
                "GET:/api/v2/services/promotion/coupons/list","GET:/api/v2/services/promotion/user-coupons/**","POST:/api/v2/services/promotion/user-coupons/available","POST:/api/v2/services/promotion/user-coupons/discount",
                "GET:/api/v2/services/media/medias/signature/play","POST:/api/v2/services/remark/likes","GET:/api/v2/services/remark/likes/list",
                "POST:/api/v2/services/user/account-privacy/export","POST:/api/v2/services/user/account-privacy/deletion","POST:/api/v2/services/user/account-privacy/consent",
                "GET:/api/v2/services/user/users/me","PUT:/api/v2/services/user/users","GET:/api/v2/services/message/inboxes","PUT:/api/v2/services/message/inboxes/*/read");
            if(studentPaths.stream().noneMatch(pattern->matcher.match(pattern,ant))) throw new ForbiddenException("该接口不向学生开放");
        }
        if(path.startsWith("/api/v2/teacher/") && (!result.success() || !java.util.Set.of(1L,3L).contains(result.getData().getRoleId())))
            throw new ForbiddenException("需要教师权限");
        String permissionPath=path;
        java.util.Map<String,String> aliases=java.util.Map.ofEntries(java.util.Map.entry("auth","as"),java.util.Map.entry("course","cs"),java.util.Map.entry("user","us"),java.util.Map.entry("learning","ls"),java.util.Map.entry("trade","ts"),java.util.Map.entry("promotion","prs"),java.util.Map.entry("exam","es"),java.util.Map.entry("media","ms"),java.util.Map.entry("search","ss"),java.util.Map.entry("pay","ps"),java.util.Map.entry("message","sms"),java.util.Map.entry("remark","rs"),java.util.Map.entry("data","ds"));
        for(var alias:aliases.entrySet()) {
            String prefix="/api/v2/services/"+alias.getKey()+"/";
            if(path.startsWith(prefix)) permissionPath="/"+alias.getValue()+"/"+path.substring(prefix.length());
        }
        auth.checkAuth(sanitized.getRequest().getMethod().name()+":"+permissionPath,result,policy.declares(sanitized.getRequest().getMethod().name(),path));
        if(result.success()) {
            LoginUserDTO user=result.getData();
            if(user.getUserId()==null || user.getRoleId()==null) throw new UnauthorizedException("身份信息不完整");
            limits.authenticated(user.getUserId());
            sanitized=sanitized.mutate().request(b->b.headers(h->{
                h.set("user-session",user.getSessionId());h.set(USER_HEADER,user.getUserId().toString());h.set("user-role",user.getRoleId().toString());
            })).build();
        }
        return chain.filter(sanitized);
    }
    @Override public int getOrder() {return -1000;}
}
