package com.tianji.gateway;
import com.tianji.gateway.config.AccessPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AccessPolicyTest {
 @Test void retiredStudentPasswordAliasesHaveNoExternalGrant(){
  var policy=new AccessPolicy();
  for(String audience:new String[]{"services","admin"})
   assertFalse(policy.declares("PUT","/api/v2/"+audience+"/user/students/password"));
 }
 @Test void learningCourseHeadcountsRemainPrivateServiceEndpoints(){
  var policy=new AccessPolicy();
  for(String audience:new String[]{"services","admin"})for(String route:new String[]{"lessons/42/count","lessons/lessons/42/count"})
   assertFalse(policy.declares("GET","/api/v2/"+audience+"/learning/"+route));
 }
 @Test void unknownMethodAndRouteNeverInheritALoginGrant(){var policy=new AccessPolicy();assertTrue(policy.declares("GET","/api/v2/notes"));assertTrue(policy.declares("GET","/api/v2/admin/course/courses/page"));assertFalse(policy.declares("GET","/api/v2/admin/user/users/list"));assertFalse(policy.declares("DELETE","/api/v2/auth/accounts/login"));assertFalse(policy.declares("GET","/api/v2/services/user/users/list"));assertFalse(policy.declares("GET","/api/v2/services/user/internal/v2/users/1/session-identity"));assertFalse(policy.declares("POST","/api/v2/unconfigured-action"));}
 @Test void declaredParameterizedRoutesMatchTheirMethodOnly(){var policy=new AccessPolicy();assertTrue(policy.declares("DELETE","/api/v2/auth/accounts/sessions/123"));assertFalse(policy.declares("POST","/api/v2/auth/accounts/sessions/123"));assertTrue(policy.declares("GET","/api/v2/teacher/exam-attempts/page"));}
 @Test void providerReconciliationDeclaresOnlyTheFourAdministrativeMethods(){
  var policy=new AccessPolicy();
  assertTrue(policy.declares("GET","/api/v2/admin/provider-payments/anomalies"));
  assertTrue(policy.declares("GET","/api/v2/admin/provider-payments/reconciliations"));
  assertTrue(policy.declares("PUT","/api/v2/admin/provider-payments/anomalies/123/AMOUNT_MISMATCH"));
  assertTrue(policy.declares("POST","/api/v2/admin/provider-payments/reconciliations/123/replay"));
  assertFalse(policy.declares("POST","/api/v2/admin/provider-payments/anomalies/123/AMOUNT_MISMATCH"));
  assertFalse(policy.declares("GET","/api/v2/admin/provider-payments/reconciliations/123/replay"));
  assertFalse(policy.declares("PUT","/api/v2/services/provider-payments/anomalies/123/AMOUNT_MISMATCH"));
  assertFalse(policy.declares("DELETE","/api/v2/admin/provider-payments/anomalies"));
  assertFalse(policy.declares("PUT","/api/v2/admin/provider-payments/anomalies/123/NOT_A_DECLARED_REASON"));
  assertFalse(policy.declares("PUT","/api/v2/admin/provider-payments/anomalies/not-a-number/AMOUNT_MISMATCH"));
 }
 @Test @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="TJ_INTERNAL_TOKEN",matches=".{32,}")
 void providerPaymentAdministrationRejectsAnonymousStudentsAndTeachers(){
  var auth=org.mockito.Mockito.mock(com.tianji.authsdk.gateway.util.AuthUtil.class);
  var filter=new com.tianji.gateway.filter.AccountAuthFilter(auth,new com.tianji.gateway.config.AuthProperties(),new AccessPolicy(),reactor.core.scheduler.Schedulers.immediate(),new com.tianji.gateway.config.TrustedProxyIdentity(new org.springframework.mock.env.MockEnvironment()));
  String[][] routes={{"GET","/api/v2/admin/provider-payments/anomalies"},{"GET","/api/v2/admin/provider-payments/reconciliations"},{"PUT","/api/v2/admin/provider-payments/anomalies/123/AMOUNT_MISMATCH"},{"POST","/api/v2/admin/provider-payments/reconciliations/123/replay"}};
  for(String[] route:routes){
   var request=org.springframework.mock.http.server.reactive.MockServerHttpRequest.method(org.springframework.http.HttpMethod.valueOf(route[0]),route[1]).header("authorization","Bearer test").build();
   for(Long role:new Long[]{null,2L,3L}){
    if(role==null)org.mockito.Mockito.when(auth.parseToken("test")).thenReturn(com.tianji.common.domain.R.error(401,"login required"));
    else {var user=new com.tianji.common.domain.dto.LoginUserDTO();user.setUserId(123L);user.setRoleId(role);org.mockito.Mockito.when(auth.parseToken("test")).thenReturn(com.tianji.common.domain.R.ok(user));}
    Class<? extends RuntimeException> expected=role==null?com.tianji.common.exceptions.UnauthorizedException.class:com.tianji.common.exceptions.ForbiddenException.class;
    assertThrows(expected,()->filter.filter(org.springframework.mock.web.server.MockServerWebExchange.from(request),exchange->{fail("Non-admin request reached the payment service");return reactor.core.publisher.Mono.empty();}).block());
   }
  }
 }
}
