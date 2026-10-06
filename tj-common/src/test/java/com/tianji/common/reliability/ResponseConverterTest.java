package com.tianji.common.reliability;
import com.tianji.common.autoconfigure.mvc.converter.WrapperResponseMessageConverter;
import com.tianji.common.domain.R;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.*;
import org.springframework.web.context.request.*;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
class ResponseConverterTest {
 @Test void malformedJsonReturnsClientErrorWithoutParserDetails(){
  var request=new MockHttpServletRequest("POST","/api/v2/bad");var response=new MockHttpServletResponse();
  RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request,response));
  var advice=new com.tianji.common.autoconfigure.mvc.advice.CommonExceptionAdvice();
  var result=(org.springframework.http.ResponseEntity<?>)advice.handleRequestFormatException(new org.springframework.http.converter.HttpMessageNotReadableException("private parser detail",new org.springframework.mock.http.MockHttpInputMessage(new byte[0])));
  assertEquals(400,result.getStatusCode().value());assertEquals("请求参数缺失或格式错误",((R<?>)result.getBody()).getMsg());
 }
 @AfterEach void cleanup(){RequestContextHolder.resetRequestAttributes();}
 @Test void applicationStartupDoesNotRequireAnHttpRequest(){
  var converter=new WrapperResponseMessageConverter(new JacksonJsonHttpMessageConverter(JsonMapper.builder().build()));
  assertFalse(converter.canWrite(String.class,MediaType.APPLICATION_JSON));
 }
 @Test void nativeV2StringsUseJsonEnvelopeInsteadOfTheStringConverter() throws Exception {
  var request=new MockHttpServletRequest("POST","/api/v2/token");RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  var converter=new WrapperResponseMessageConverter(new JacksonJsonHttpMessageConverter(JsonMapper.builder().build()));
  assertTrue(converter.canWrite(String.class,MediaType.APPLICATION_JSON));
  var response=new MockHttpServletResponse();converter.write(R.ok("token"),MediaType.APPLICATION_JSON,new org.springframework.http.server.ServletServerHttpResponse(response));
  assertEquals("token",JsonMapper.builder().build().readTree(response.getContentAsString()).get("data").asString());
 }
}
