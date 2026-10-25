package com.tianji.common.reliability;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tianji.common.autoconfigure.mvc.advice.CommonExceptionAdvice;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.*;
import org.springframework.validation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;

class CredentialValidationLoggingTest {
 public void form(Object ignored){}
 @Test void bindingFailuresNeverLogRejectedCredentialsEvenAtDebugLevel()throws Exception {
  var logger=(Logger)LoggerFactory.getLogger(CommonExceptionAdvice.class);Level previous=logger.getLevel();
  var events=new ListAppender<ILoggingEvent>();events.start();logger.addAppender(events);logger.setLevel(Level.DEBUG);
  var request=new MockHttpServletRequest();request.setRequestURI("/api/v2/auth/accounts/password/initial");
  RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request,new MockHttpServletResponse()));
  try{
   var binding=new BeanPropertyBindingResult(new Object(),"initialPassword");
   binding.addError(new FieldError("initialPassword","newPassword","sensitive-rejected-value",false,new String[]{"Size"},null,"length must be between 8 and 72"));
   var advice=new CommonExceptionAdvice();
   advice.handleMethodArgumentNotValidException(new MethodArgumentNotValidException(new MethodParameter(getClass().getMethod("form",Object.class),0),binding));
   advice.handleBindException(new BindException(binding));
   assertEquals(2,events.list.size());
   for(var event:events.list){assertFalse(event.getFormattedMessage().contains("sensitive-rejected-value"));assertNull(event.getThrowableProxy());}
  }finally{RequestContextHolder.resetRequestAttributes();logger.detachAppender(events);logger.setLevel(previous);events.stop();}
 }
}
