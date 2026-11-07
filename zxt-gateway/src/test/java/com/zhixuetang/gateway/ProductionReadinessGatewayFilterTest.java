package com.zhixuetang.gateway;

import com.zhixuetang.authsdk.gateway.util.AuthUtil;
import com.zhixuetang.common.domain.R;
import com.zhixuetang.common.domain.dto.LoginUserDTO;
import com.zhixuetang.common.exceptions.*;
import com.zhixuetang.gateway.config.*;
import com.zhixuetang.gateway.filter.AccountAuthFilter;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpMethod;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the actual public authorization filter, not just the declaration inventory. */
class ProductionReadinessGatewayFilterTest {
    private static final String UUID = "23456789-2345-2345-2345-23456789abcd";
    private static final String TASKS = "/api/v2/services/user/account-privacy/tasks";
    private AuthUtil auth;
    private AccountAuthFilter filter;

    @BeforeEach
    void prepare() {
        auth = mock(AuthUtil.class);
        filter =
                new AccountAuthFilter(
                        auth,
                        new AuthProperties(),
                        new AccessPolicy(),
                        Schedulers.immediate(),
                        new TrustedProxyIdentity(new MockEnvironment()));
        login(2L);
    }

    private void login(long role) {
        var user = new LoginUserDTO();
        user.setUserId(701L);
        user.setRoleId(role);
        user.setSessionId("verified-session");
        when(auth.parseToken("fixture")).thenReturn(R.ok(user));
    }

    private MockServerWebExchange exchange(String method, String path, boolean loggedIn) {
        var request =
                MockServerHttpRequest.method(HttpMethod.valueOf(method), path)
                        .header("user-info", "999")
                        .header("user-role", "1")
                        .header("user-session", "forged-session")
                        .header("X-Internal-Token", "forged-internal")
                        .header("X-ZXT-Service", "identity");
        if (loggedIn) request.header("Authorization", "Bearer fixture");
        return MockServerWebExchange.from(request.build());
    }

    private ServerWebExchange forward(String method, String path) {
        var forwarded = new AtomicReference<ServerWebExchange>();
        filter.filter(
                        exchange(method, path, true),
                        next -> {
                            forwarded.set(next);
                            return Mono.empty();
                        })
                .block(Duration.ofSeconds(2));
        assertNotNull(forwarded.get(), method + " " + path);
        return forwarded.get();
    }

    private void denied(
            Class<? extends RuntimeException> failure,
            String method,
            String path,
            boolean loggedIn) {
        assertThrows(
                failure,
                () ->
                        filter.filter(
                                        exchange(method, path, loggedIn),
                                        next -> {
                                            fail(
                                                    "Forbidden request reached downstream: "
                                                            + method
                                                            + " "
                                                            + path);
                                            return Mono.empty();
                                        })
                                .block(Duration.ofSeconds(2)));
    }

    @Test
    void studentSelfServicePathsForwardOnlyTheVerifiedIdentity() {
        for (String[] route :
                List.of(
                        new String[] {"GET", TASKS},
                        new String[] {"POST", TASKS},
                        new String[] {"GET", TASKS + "/" + UUID},
                        new String[] {"POST", TASKS + "/" + UUID + "/retry"},
                        new String[] {"POST", TASKS + "/" + UUID + "/download"})) {
            var headers = forward(route[0], route[1]).getRequest().getHeaders();
            assertEquals("701", headers.getFirst("user-info"));
            assertEquals("2", headers.getFirst("user-role"));
            assertEquals("verified-session", headers.getFirst("user-session"));
            assertNull(headers.getFirst("X-Internal-Token"));
            assertNull(headers.getFirst("X-ZXT-Service"));
        }
        verify(auth, times(5)).checkAuth(anyString(), any(), eq(true));
    }

    @Test
    void studentsAndTeachersCannotApproveTasksOrEnterMediaAdministrationThroughEitherAlias() {
        for (long role : new long[] {2, 3}) {
            login(role);
            denied(ForbiddenException.class, "POST", TASKS + "/" + UUID + "/approve", true);
            denied(
                    ForbiddenException.class,
                    "GET",
                    "/api/v2/admin/user/account-privacy/tasks",
                    true);
            denied(
                    ForbiddenException.class,
                    "POST",
                    "/api/v2/admin/user/account-privacy/tasks/" + UUID + "/approve",
                    true);
            for (String audience : List.of("services", "admin")) {
                denied(
                        ForbiddenException.class,
                        "GET",
                        "/api/v2/" + audience + "/media/media-trials",
                        true);
                denied(
                        ForbiddenException.class,
                        "POST",
                        "/api/v2/" + audience + "/media/media-trials/" + "a".repeat(64) + "/retry",
                        true);
            }
        }
        verify(auth, never()).checkAuth(anyString(), any(), anyBoolean());
    }

    @Test
    void anonymousTaskRequestsRequireLoginWhileSignedTrialGetStillBypassesAccountParsing() {
        when(auth.parseToken(null)).thenReturn(R.error(401, "login required"));
        denied(UnauthorizedException.class, "GET", TASKS, false);
        denied(UnauthorizedException.class, "POST", TASKS, false);
        denied(UnauthorizedException.class, "GET", TASKS + "/" + UUID, false);
        denied(UnauthorizedException.class, "POST", TASKS + "/" + UUID + "/download", false);
        var forwarded = new AtomicReference<ServerWebExchange>();
        filter.filter(
                        exchange(
                                "GET",
                                "/api/v2/services/media/trial-content/" + "a".repeat(64),
                                false),
                        next -> {
                            forwarded.set(next);
                            return Mono.empty();
                        })
                .block(Duration.ofSeconds(2));
        assertNotNull(forwarded.get());
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("user-info"));
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-ZXT-Service"));
        verify(auth, times(4)).parseToken(null);
    }

    @Test
    void invalidIdentifiersFailBeforeAccountParsingOrDownstreamForwarding() {
        for (String invalid : List.of("123", "-".repeat(36), "a".repeat(64), UUID.replace("-", "")))
            denied(ForbiddenException.class, "GET", TASKS + "/" + invalid, true);
        for (String invalid : List.of(UUID, "a".repeat(63), "a".repeat(65), "g".repeat(64))) {
            denied(
                    ForbiddenException.class,
                    "GET",
                    "/api/v2/services/media/trial-content/" + invalid,
                    false);
            denied(
                    ForbiddenException.class,
                    "POST",
                    "/api/v2/admin/media/media-trials/" + invalid + "/retry",
                    true);
        }
        verifyNoInteractions(auth);
    }

    @Test
    void administratorCanReachTheDeclaredApprovalAndMediaRetryRoutes() {
        login(1);
        assertNotNull(
                forward("POST", "/api/v2/admin/user/account-privacy/tasks/" + UUID + "/approve"));
        assertNotNull(
                forward("POST", "/api/v2/admin/media/media-trials/" + "a".repeat(64) + "/retry"));
    }
}
