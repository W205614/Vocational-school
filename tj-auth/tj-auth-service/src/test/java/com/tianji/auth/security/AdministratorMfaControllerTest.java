package com.tianji.auth.security;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.LoginFormDTO;
import com.tianji.auth.controller.AdministratorMfaController;
import com.tianji.auth.service.impl.*;
import com.tianji.common.domain.dto.LoginUserDTO;
import com.tianji.common.exceptions.*;
import com.tianji.common.utils.InternalAuth;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdministratorMfaControllerTest {
    private final UserClient users=mock(UserClient.class);
    private final LoginProtection protection=mock(LoginProtection.class);
    private final AdministratorMfaService mfa=mock(AdministratorMfaService.class);
    private final AdministratorMfaController controller=new AdministratorMfaController(mfa,users,protection);
    private final LoginFormDTO form=new LoginFormDTO();
    private MockedStatic<InternalAuth> identity;

    @BeforeEach void setup(){
        identity=mockStatic(InternalAuth.class);
        form.setType(1);form.setUsername("fixture-admin");form.setPassword("fixture-password");
        var request=new MockHttpServletRequest();request.addHeader("X-Client-IP","127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    @AfterEach void cleanup(){identity.close();RequestContextHolder.resetRequestAttributes();}

    private feign.FeignException remote(int status){
        return feign.FeignException.errorStatus("mfa",feign.Response.builder().status(status).reason("fixture")
            .request(feign.Request.create(feign.Request.HttpMethod.POST,"http://fixture/users/detail/true",Map.of(),new byte[0],java.nio.charset.StandardCharsets.UTF_8)).build());
    }
    @Test void invalidCredentialsStillCountAsAuthenticationFailure(){
        when(users.queryUserDetail(form,true)).thenThrow(new BadRequestException("invalid"));
        assertThrows(BadRequestException.class,()->controller.enroll(form));
        verify(protection).failed("fixture-admin","127.0.0.1");verifyNoInteractions(mfa);
    }
    @Test void remoteCredentialRejectionStillCounts(){
        when(users.queryUserDetail(form,true)).thenThrow(remote(401));
        assertThrows(BadRequestException.class,()->controller.enroll(form));
        verify(protection).failed("fixture-admin","127.0.0.1");
    }
    @Test void remoteOutageDoesNotCountAsCredentialFailure(){
        when(users.queryUserDetail(form,true)).thenThrow(remote(503));
        assertThrows(ServiceUnavailableException.class,()->controller.enroll(form));
        verify(protection,never()).failed(anyString(),anyString());verifyNoInteractions(mfa);
    }
    @Test void typedOutageAndUnexpectedErrorsPropagateWithoutCounting(){
        var outage=new ServiceUnavailableException("identity unavailable");
        when(users.queryUserDetail(form,true)).thenThrow(outage);
        assertSame(outage,assertThrows(ServiceUnavailableException.class,()->controller.enroll(form)));
        var unexpected=new IllegalStateException("fixture error");doThrow(unexpected).when(users).queryUserDetail(form,true);
        assertSame(unexpected,assertThrows(IllegalStateException.class,()->controller.enroll(form)));
        verify(protection,never()).failed(anyString(),anyString());
    }
    @Test void currentIdentityOutageDoesNotCountAndVerifiedAdministratorCanEnroll(){
        var user=new LoginUserDTO();user.setUserId(42L);user.setRoleId(1L);
        when(users.queryUserDetail(form,true)).thenReturn(user);when(users.sessionIdentity(42)).thenThrow(remote(503));
        assertThrows(ServiceUnavailableException.class,()->controller.enroll(form));verify(protection,never()).failed(anyString(),anyString());
        doReturn(user).when(users).sessionIdentity(42);controller.enroll(form);verify(mfa).enroll(42);
    }
    @Test void nonAdministratorCannotEnroll(){
        var user=new LoginUserDTO();user.setUserId(42L);user.setRoleId(3L);
        when(users.queryUserDetail(form,true)).thenReturn(user);when(users.sessionIdentity(42)).thenReturn(user);
        assertThrows(BadRequestException.class,()->controller.enroll(form));verifyNoInteractions(mfa);
    }
    @Test void confirmationCountsInvalidCodesButNotAnUnavailableMfaComponent(){
        var user=new LoginUserDTO();user.setUserId(42L);user.setRoleId(1L);
        when(users.queryUserDetail(form,true)).thenReturn(user);when(users.sessionIdentity(42)).thenReturn(user);
        doThrow(new ServiceUnavailableException("MFA not configured")).when(mfa).confirm(42,null);
        assertThrows(ServiceUnavailableException.class,()->controller.confirm(form));verify(protection,never()).failed(anyString(),anyString());
        doThrow(new BadRequestException("invalid code")).when(mfa).confirm(42,null);
        assertThrows(BadRequestException.class,()->controller.confirm(form));verify(protection).failed("fixture-admin","127.0.0.1");
    }
}
