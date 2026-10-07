package com.tianji.auth.security;
import com.tianji.auth.service.impl.*;
import com.tianji.auth.service.ILoginRecordService;
import com.tianji.auth.util.JwtTool;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.LoginFormDTO;
import com.tianji.common.exceptions.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
class LoginFailureClassificationTest {
 private LoginProtection protection;private UserClient users;private AccountServiceImpl service;private LoginFormDTO form;
 @BeforeEach void setup(){protection=mock(LoginProtection.class);users=mock(UserClient.class);service=new AccountServiceImpl(protection,mock(SessionStore.class),mock(JwtTool.class),users,mock(ILoginRecordService.class));form=new LoginFormDTO();form.setType(1);form.setUsername("fixture");form.setPassword("invalid");var request=new MockHttpServletRequest();request.addHeader("X-Client-IP","127.0.0.1");RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));}
 @AfterEach void cleanup(){RequestContextHolder.resetRequestAttributes();}
 private feign.FeignException remote(int status){return feign.FeignException.errorStatus("login",feign.Response.builder().status(status).reason("test").request(feign.Request.create(feign.Request.HttpMethod.POST,"http://local/users/detail/false",Map.of(),new byte[0],java.nio.charset.StandardCharsets.UTF_8)).build());}
 @Test void remoteInvalidCredentialsAreCounted(){when(users.queryUserDetail(form,false)).thenThrow(remote(400));assertThrows(BadRequestException.class,()->service.login(form,false));verify(protection).failed("fixture","127.0.0.1");}
 @Test void upstreamOutageDoesNotLockAccounts(){when(users.queryUserDetail(form,false)).thenThrow(remote(503));assertThrows(ServiceUnavailableException.class,()->service.login(form,false));verify(protection,never()).failed(anyString(),anyString());}
}
