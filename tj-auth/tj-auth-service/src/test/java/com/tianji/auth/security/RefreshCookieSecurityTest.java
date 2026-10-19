package com.tianji.auth.security;

import com.tianji.auth.service.impl.AccountServiceImpl;
import com.tianji.auth.service.impl.LoginProtection;
import com.tianji.auth.service.impl.SessionStore;
import com.tianji.auth.service.ILoginRecordService;
import com.tianji.auth.util.JwtTool;
import com.tianji.api.client.user.UserClient;
import com.tianji.common.exceptions.ForbiddenException;
import com.tianji.common.utils.CookieBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named="TJ_INTERNAL_TOKEN",matches=".{32,}")
class RefreshCookieSecurityTest {
    private AccountServiceImpl service;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    @BeforeEach void setup(){
        service=new AccountServiceImpl(mock(LoginProtection.class),mock(SessionStore.class),mock(JwtTool.class),mock(UserClient.class),mock(ILoginRecordService.class));
        request=new MockHttpServletRequest();response=new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request,response));
    }
    @AfterEach void cleanup(){RequestContextHolder.resetRequestAttributes();}
    private CookieBuilder cookie(){return ReflectionTestUtils.invokeMethod(service,"refreshCookie");}
    @Test void trustedGatewayHttpsMarksRefreshCookieSecure(){
        request.addHeader("X-Internal-Token",System.getenv("TJ_INTERNAL_TOKEN"));request.addHeader("X-Client-Proto","https");
        cookie().name("refresh").value("test").build();assertTrue(response.getCookie("refresh").getSecure());
    }
    @Test void productionCookiePolicySurvivesHttpUpstreamAndLocalHttpStaysUsable(){
        request.addHeader("X-Internal-Token",System.getenv("TJ_INTERNAL_TOKEN"));request.addHeader("X-Client-Proto","http");
        cookie().name("local").value("test").build();assertFalse(response.getCookie("local").getSecure());
        ReflectionTestUtils.setField(service,"requireSecureCookie",true);
        cookie().name("production").value("test").build();assertTrue(response.getCookie("production").getSecure());
    }
    @Test void forwardedProtocolWithoutServiceIdentityIsRejected(){
        request.addHeader("X-Client-Proto","https");assertThrows(ForbiddenException.class,this::cookie);
    }
}
