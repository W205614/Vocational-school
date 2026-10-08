package com.tianji.authsdk.resource.interceptors;
import com.tianji.common.utils.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class UserInfoInterceptorDepthTest {
 @AfterEach void clean(){UserContext.removeUser();}
 private HttpServletRequest request(String depth,String user){var request=mock(HttpServletRequest.class);when(request.getRequestURI()).thenReturn("/internal/v1/user");when(request.getHeader("X-TJ-Call-Depth")).thenReturn(depth);when(request.getHeader("user-info")).thenReturn(user);return request;}
 @Test void anonymousInternalCallsStillPropagateDepthAndClearItAfterDispatch() {
  try(var auth=mockStatic(InternalAuth.class)){
   var request=request("2",null);var response=mock(HttpServletResponse.class);var interceptor=new UserInfoInterceptor();
   assertTrue(interceptor.preHandle(request,response,null));assertEquals(2,UserContext.getCallDepth());
   interceptor.afterCompletion(request,response,null,null);assertEquals(0,UserContext.getCallDepth());assertNull(UserContext.getUser());
  }
 }
 @Test void malformedIdentityOrDepthClearsEveryThreadLocal() {
  try(var auth=mockStatic(InternalAuth.class)){
   for(var request:new HttpServletRequest[]{request("5",null),request("bad",null),request("2","bad")}) {
    UserContext.setUser(99L);UserContext.setRole(1L);UserContext.setSession("old");
    assertThrows(com.tianji.common.exceptions.UnauthorizedException.class,()->new UserInfoInterceptor().preHandle(request,mock(HttpServletResponse.class),null));
    assertEquals(0,UserContext.getCallDepth());assertNull(UserContext.getUser());assertNull(UserContext.getRole());assertNull(UserContext.getSession());
   }
  }
 }
}
