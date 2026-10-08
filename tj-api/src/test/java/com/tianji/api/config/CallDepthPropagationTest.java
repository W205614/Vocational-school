package com.tianji.api.config;
import com.tianji.common.utils.UserContext;
import feign.RequestTemplate;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class CallDepthPropagationTest {
 @AfterEach void clean(){UserContext.removeUser();}
 @Test void nestedHttpDepthPropagatesAndCannotLeakIntoTheNextCaller() {
  var interceptor=new RequestIdRelayConfiguration().requestIdInterceptor();UserContext.setUser(7L);UserContext.setRole(2L);UserContext.setCallDepth(2);
  var nested=new RequestTemplate();interceptor.apply(nested);assertTrue(nested.headers().get("X-TJ-Call-Depth").contains("3"));assertTrue(nested.headers().get("user-info").contains("7"));
  UserContext.removeUser();var next=new RequestTemplate();interceptor.apply(next);assertTrue(next.headers().get("X-TJ-Call-Depth").contains("1"));assertFalse(next.headers().containsKey("user-info"));
  UserContext.setCallDepth(4);assertThrows(IllegalStateException.class,()->interceptor.apply(new RequestTemplate()));
 }
}
