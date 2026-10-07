package com.tianji.compact;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.mock.web.*;
import com.tianji.common.utils.UserContext;
import feign.*;
import java.nio.charset.StandardCharsets;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="TJ_INTERNAL_TOKEN",matches=".{32,}")
class LocalModuleTransportTest {
 @Configuration @EnableWebMvc static class Web {
  @Bean Controller controller(){return new Controller();}
 }
 @RestController static class Controller {
  @GetMapping("/lookup") Map<String,Object> lookup(@RequestParam List<Long> ids,@RequestHeader("user-info") String user){UserContext.setUser(Long.valueOf(user));return Map.of("ids",ids,"user",user);}
  @PostMapping("/echo") Map<String,Object> echo(@RequestBody Map<String,Object> input){return input;}
  @GetMapping("/blocked") Map<String,Object> blocked()throws InterruptedException{Thread.sleep(250);return Map.of("completed",true);}
 }
 private ModuleRegistry registry;private Client client;
 @BeforeEach void setup()throws Exception{var context=new GenericWebApplicationContext();context.setServletContext(new MockServletContext());new AnnotatedBeanDefinitionReader(context).register(Web.class);context.refresh();var dispatcher=new DispatcherServlet(context);dispatcher.init(new MockServletConfig(context.getServletContext(),"module-user"));registry=new ModuleRegistry();registry.add("user",context,dispatcher);client=new LocalModuleCapability(registry).enrich((Client)(r,o)->{throw new AssertionError("Unexpected network call");});}
 @AfterEach void close(){registry.close();UserContext.removeUser();}
 private Request request(Request.HttpMethod method,String path,byte[] body){return Request.create(method,"http://same-host/_modules/user"+path,Map.of("X-Internal-Token",List.of(System.getenv("TJ_INTERNAL_TOKEN")),"user-info",List.of("7"),"Content-Type",List.of("application/json")),body,StandardCharsets.UTF_8,new RequestTemplate());}
 @Test void localMvcBindingAndCallerIdentityArePreserved()throws Exception{UserContext.setUser(99L);UserContext.setRole(1L);UserContext.setSession("caller-session");var response=client.execute(request(Request.HttpMethod.GET,"/lookup?ids=1&ids=2",null),new Request.Options());assertEquals(200,response.status());String body=new String(response.body().asInputStream().readAllBytes(),StandardCharsets.UTF_8);assertTrue(body.contains("[1,2]"));assertTrue(body.contains("\"7\""));assertEquals(99L,UserContext.getUser());assertEquals(1L,UserContext.getRole());assertEquals("caller-session",UserContext.getSession());}
 @Test void postBodyUsesTargetModuleMessageConverters()throws Exception{var response=client.execute(request(Request.HttpMethod.POST,"/echo","{\"value\":\"hello\"}".getBytes(StandardCharsets.UTF_8)),new Request.Options());assertEquals(200,response.status());assertTrue(new String(response.body().asInputStream().readAllBytes(),StandardCharsets.UTF_8).contains("hello"));}
 @Test void failedBindingDoesNotLeakCallerThreadState()throws Exception{UserContext.setUser(99L);var response=client.execute(request(Request.HttpMethod.GET,"/lookup?ids=invalid",null),new Request.Options());assertEquals(400,response.status());assertEquals(99L,UserContext.getUser());}
 @Test void configuredReadDeadlineMustBoundBlockedModule() {
  assertThrows(java.io.IOException.class,()->client.execute(request(Request.HttpMethod.GET,"/blocked",null),new Request.Options(50,java.util.concurrent.TimeUnit.MILLISECONDS,50,java.util.concurrent.TimeUnit.MILLISECONDS,true)));
 }
}
