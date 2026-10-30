package com.tianji.auth.security;
import com.tianji.auth.service.impl.*;
import com.tianji.auth.service.ILoginRecordService;
import com.tianji.auth.util.JwtTool;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.*;
import com.tianji.common.domain.dto.LoginUserDTO;
import com.tianji.common.exceptions.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class InitialPasswordAuthTest {
 private LoginProtection protection;private UserClient users;private SessionStore sessions;private JwtTool tokens;private ILoginRecordService records;private AccountServiceImpl service;
 @BeforeEach void setup(){protection=mock(LoginProtection.class);users=mock(UserClient.class);sessions=mock(SessionStore.class);tokens=mock(JwtTool.class);records=mock(ILoginRecordService.class);service=new AccountServiceImpl(protection,sessions,tokens,users,records,mock(AdministratorMfaService.class));var request=new MockHttpServletRequest();request.addHeader("X-Client-IP","127.0.0.1");RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));}
 @AfterEach void cleanup(){RequestContextHolder.resetRequestAttributes();}
 @Test void temporaryLoginIssuesNeitherTokenNorRefreshSession(){
  var form=new LoginFormDTO();form.setType(1);form.setUsername("teacher");form.setPassword("temporary-fixture");
  var identity=new LoginUserDTO();identity.setUserId(42L);identity.setRoleId(3L);identity.setMustChangePassword(true);when(users.queryUserDetail(form,true)).thenReturn(identity);
  assertEquals(428,assertThrows(InitialPasswordRequiredException.class,()->service.login(form,true)).getStatus());
  verifyNoInteractions(tokens,sessions,records);verify(users,never()).sessionIdentity(anyLong());verify(protection).success("teacher");
 }
 @Test void initialChangeReusesProtectionWithoutCreatingSession(){
  var form=new InitialPasswordDTO("teacher","temporary-fixture","new-password-fixture","admin");service.completeInitialPassword(form);
  verify(protection).before("teacher","127.0.0.1");verify(users).completeInitialPassword(form);verify(protection).success("teacher");verifyNoInteractions(tokens,sessions,records);
 }
 @Test void invalidInitialCredentialCountsAsLoginFailure(){
  var form=new InitialPasswordDTO("teacher","temporary-fixture","new-password-fixture","admin");doThrow(new BadRequestException("invalid")).when(users).completeInitialPassword(form);
  assertThrows(BadRequestException.class,()->service.completeInitialPassword(form));verify(protection).failed("teacher","127.0.0.1");verifyNoInteractions(tokens,sessions,records);
 }
 @Test void initialCredentialObjectsNeverExposeSecretsThroughToString(){
  var form=new InitialPasswordDTO("teacher","old-secret","new-secret-password","admin");assertFalse(form.toString().contains("secret"));
  var receipt=new TemporaryCredentialDTO("42","teacher","temporary-secret",java.time.LocalDateTime.now());assertFalse(receipt.toString().contains("temporary-secret"));
 }
 @Test void initialPasswordMvcReturnsJsonEnvelopeAndNoStoreAfterCacheHeadersAreApplied()throws Exception{
  var accounts=mock(com.tianji.auth.service.IAccountService.class);
  var json=new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter(tools.jackson.databind.json.JsonMapper.builder().build());
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new com.tianji.auth.controller.AccountController(accounts))
    .setControllerAdvice(new com.tianji.common.autoconfigure.mvc.advice.WrapperResponseBodyAdvice())
    .setMessageConverters(new com.tianji.common.autoconfigure.mvc.converter.WrapperResponseMessageConverter(json),json).build();
  // Only the service/security dependencies are substituted; argument resolution,
  // model attributes, void handling, advice and HTTP serialization are real MVC.
  try(var identity=mockStatic(com.tianji.common.utils.InternalAuth.class)){
   var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/accounts/password/initial")
     .header("x-request-from","gateway").contentType(org.springframework.http.MediaType.APPLICATION_JSON).accept(org.springframework.http.MediaType.APPLICATION_JSON)
     .content("{\"username\":\"fixture-teacher\",\"oldPassword\":\"temporary-fixture\",\"newPassword\":\"changed-password-fixture\",\"audience\":\"admin\"}"))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store"))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Pragma","no-cache"))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_JSON))
     .andReturn().getResponse();
   var body=tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.getContentAsString());
   assertEquals(200,body.get("code").asInt());assertTrue(body.get("data").isNull());
   verify(accounts).completeInitialPassword(new InitialPasswordDTO("fixture-teacher","temporary-fixture","changed-password-fixture","admin"));
  }
 }
}
