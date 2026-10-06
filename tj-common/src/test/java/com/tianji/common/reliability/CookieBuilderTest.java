package com.tianji.common.reliability;
import com.tianji.common.utils.CookieBuilder;
import org.springframework.mock.web.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CookieBuilderTest {
 @Test void localAndPublicHostsUseHostOnlyRefreshCookies(){
  for(String host:new String[]{"127.0.0.1","localhost","school.example.com"}){
   var request=new MockHttpServletRequest();request.setServerName(host);var response=new MockHttpServletResponse();
   new CookieBuilder(request,response).name("refresh").value("private-token").httpOnly(true).build();
   var cookie=response.getCookie("refresh");assertNotNull(cookie);assertNull(cookie.getDomain());assertTrue(cookie.isHttpOnly());assertEquals("Lax",cookie.getAttribute("SameSite"));
  }
 }
 @Test void secureRequestProducesSecureCookie(){var request=new MockHttpServletRequest();request.setSecure(true);var response=new MockHttpServletResponse();new CookieBuilder(request,response).name("refresh").value("token").build();assertTrue(response.getCookie("refresh").getSecure());}
}
