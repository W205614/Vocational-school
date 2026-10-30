package com.tianji.auth.security;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.LoginFormDTO;
import com.tianji.auth.service.ILoginRecordService;
import com.tianji.auth.service.impl.*;
import com.tianji.auth.util.JwtTool;
import com.tianji.common.domain.dto.LoginUserDTO;
import com.tianji.common.exceptions.UnauthorizedException;
import com.tianji.common.utils.InternalAuth;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.*;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginCredentialVersionTest {
    private UserClient users;private JwtTool tokens;private SessionStore sessions;
    private ILoginRecordService records;private AccountServiceImpl service;private LoginFormDTO form;
    private AdministratorMfaService mfa;
    private MockHttpServletResponse response;
    @BeforeEach void setup(){
        users=mock(UserClient.class);tokens=mock(JwtTool.class);sessions=mock(SessionStore.class);records=mock(ILoginRecordService.class);
        mfa=mock(AdministratorMfaService.class);
        service=new AccountServiceImpl(mock(LoginProtection.class),sessions,tokens,users,records,mfa);
        form=new LoginFormDTO();form.setType(1);form.setUsername("credential-version-fixture");form.setPassword("old-password-fixture");
        var request=new MockHttpServletRequest();request.addHeader("X-Client-IP","127.0.0.1");response=new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request,response));
    }
    @AfterEach void cleanup(){RequestContextHolder.resetRequestAttributes();}
    private LoginUserDTO identity(Long version,long role){var value=new LoginUserDTO();value.setUserId(42L);value.setRoleId(role);value.setAuthVersion(version);return value;}
    private void snapshots(LoginUserDTO verified,LoginUserDTO current){
        when(users.queryUserDetail(form,false)).thenReturn(verified);when(users.sessionIdentity(42L)).thenReturn(current);
    }
    @Test void passwordResetBetweenCredentialVerificationAndIdentityReadCannotUpgradeOldProof(){
        // The first RPC has accepted the old password; a committed reset advances
        // the version before the second RPC, which must invalidate that proof.
        snapshots(identity(7L,2),identity(8L,2));
        assertThrows(UnauthorizedException.class,()->service.login(form,false));
        verifyNoInteractions(tokens,sessions,records);assertTrue(response.getHeaders("Set-Cookie").isEmpty());
    }
    @Test void roleChangeBetweenReadsAlsoRejectsTheCredentialSnapshot(){
        snapshots(identity(7L,2),identity(7L,3));
        assertThrows(UnauthorizedException.class,()->service.login(form,false));verifyNoInteractions(tokens,sessions,records);
    }
    @Test void missingCredentialVersionFailsClosedRatherThanAdoptingTheCurrentVersion(){
        snapshots(identity(null,2),identity(8L,2));
        assertThrows(UnauthorizedException.class,()->service.login(form,false));verifyNoInteractions(tokens,sessions,records);
    }
    @Test void unchangedCredentialVersionIsRecordedBeforeTokensAreCreated(){
        var verified=identity(7L,2);snapshots(verified,identity(7L,2));
        when(tokens.createRefreshToken(verified)).thenReturn("refresh-fixture");when(tokens.createToken(verified)).thenReturn("access-fixture");
        try(var internal=mockStatic(InternalAuth.class)){
            assertEquals("access-fixture",service.login(form,false));
        }
        var order=inOrder(records,tokens);order.verify(records).loginSuccess(null,42L);order.verify(tokens).createRefreshToken(verified);order.verify(tokens).createToken(verified);
        assertEquals(7L,verified.getAuthVersion());assertFalse(response.getHeaders("Set-Cookie").isEmpty());
        verify(mfa).verifyLogin(verified,null);
    }
    @Test void durableRecordFailureCannotCreateTokensCookiesOrAnMfaSession(){
        var verified=identity(7L,1);when(users.queryUserDetail(form,true)).thenReturn(verified);when(users.sessionIdentity(42L)).thenReturn(identity(7L,1));
        when(mfa.verifyLogin(verified,null)).thenReturn(true);
        doThrow(new DataAccessResourceFailureException("fixture unavailable")).when(records).loginSuccess(null,42L);
        assertThrows(DataAccessResourceFailureException.class,()->service.login(form,true));
        verifyNoInteractions(tokens,sessions);verify(mfa,never()).markSession(any());assertTrue(response.getHeaders("Set-Cookie").isEmpty());
    }
}
