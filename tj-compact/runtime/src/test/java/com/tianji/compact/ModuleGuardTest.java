package com.tianji.compact;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class ModuleGuardTest {
 private final String token="synthetic-service-identity-"+"x".repeat(40);
 private HttpServletRequest request(int port,String depth,String credential) {
  var request=mock(HttpServletRequest.class);when(request.getRequestURI()).thenReturn("/_modules/user/users/me");
  when(request.getLocalPort()).thenReturn(port);when(request.getHeader("X-TJ-Call-Depth")).thenReturn(depth);
  when(request.getHeader("X-Internal-Token")).thenReturn(credential);return request;
 }
 @Test void reservedConnectorsRequireServiceIdentityAndTheirExactCallDepth()throws Exception {
  for(var bad:new HttpServletRequest[]{request(25001,"1",null),request(25001,"1","x".repeat(64)),request(25002,"1",token),request(25004,"5",token)}) {
   var response=mock(HttpServletResponse.class);var next=mock(FilterChain.class);
   new ModuleGuard(25001,token).doFilter(bad,response,next);verify(response).setStatus(403);verifyNoInteractions(next);
  }
  var valid=request(25003,"3",token);var response=mock(HttpServletResponse.class);var next=mock(FilterChain.class);
  new ModuleGuard(25001,token).doFilter(valid,response,next);verify(next).doFilter(valid,response);
 }
 @Test void publicConnectorCannotImpersonateReservedCapacity()throws Exception {
  var bad=request(24001,"1",token);var response=mock(HttpServletResponse.class);var next=mock(FilterChain.class);
  new ModuleGuard(25001,token).doFilter(bad,response,next);verify(response).setStatus(403);verifyNoInteractions(next);
  var valid=request(24001,null,token);new ModuleGuard(25001,token).doFilter(valid,response,next);verify(next).doFilter(valid,response);
 }
}
