package com.tianji.user.reliability;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sun.net.httpserver.HttpServer;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.config.RequestIdRelayConfiguration;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.authsdk.resource.interceptors.*;
import com.tianji.common.autoconfigure.mvc.advice.CommonExceptionAdvice;
import com.tianji.common.autoconfigure.reliability.*;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.service.impl.*;
import com.tianji.user.controller.*;
import feign.Feign;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Real socket -> production Feign relay -> real identity controller/auth interceptors/SQL.
 * Production OperationWorker/Store, points and outbox use a dedicated real MySQL schema.
 * Only course metadata/assignment is a contract double. No request user is installed on workers. */
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class LearningAdoptionAsyncTest {
    private static DriverManagerDataSource source;
    private JdbcTemplate jdbc;private JsonMapper json;private long teacher,student,author,course,question,reply;
    private OperationStore store;private OperationWorker worker;private ThreadPoolTaskExecutor executor;
    private HttpServer server;private ExecutorService httpExecutor;private UserClient users;
    private final List<String> operations=new ArrayList<>();
    private final AtomicBoolean unavailable=new AtomicBoolean();
    private final AtomicInteger internalCalls=new AtomicInteger(),actorHeaders=new AtomicInteger();
    private String url;

    @BeforeAll static void schema()throws Exception {
        String options="?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true";
        String base="jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/";
        new JdbcTemplate(new DriverManagerDataSource(base+options,"root",System.getenv("ACCEPTANCE_DB_PASSWORD"))).execute("CREATE DATABASE IF NOT EXISTS acceptance_adoption CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        source=new DriverManagerDataSource(base+"acceptance_adoption"+options,"root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
        var db=new JdbcTemplate(source);assertEquals("acceptance_adoption",db.queryForObject("SELECT DATABASE()",String.class));
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while(root!=null && !Files.isRegularFile(root.resolve("deploy/acceptance/test-schema.sql")))root=root.getParent();
        assertNotNull(root,"Repository baseline is required");
        String baseline=Files.readString(root.resolve("deploy/acceptance/test-schema.sql"));
        baseline=baseline.split("USE `acceptance_learning`;",2)[1].split("USE `",2)[0];
        var definitions=Pattern.compile("CREATE TABLE(?: IF NOT EXISTS)? (?:`[^`]+`|[A-Za-z_]+)\\s*\\(.*?\\)\\s*ENGINE=.*?;",Pattern.DOTALL).matcher(baseline);
        int count=0;while(definitions.find()){db.execute(ifAbsent(definitions.group()));count++;}assertEquals(12,count);
        String bootstrap=Files.readString(root.resolve("deploy/compact/schema.sql"));
        for(String table:List.of("interaction_question","interaction_reply","user","user_detail")){
            var definition=Pattern.compile("CREATE TABLE `"+table+"` \\(.*?\\) ENGINE=.*?;",Pattern.DOTALL).matcher(bootstrap);
            assertTrue(definition.find(),"Missing canonical table "+table);db.execute(ifAbsent(definition.group()));
        }
        db.execute("CREATE TABLE IF NOT EXISTS schema_migration(version VARCHAR(128) PRIMARY KEY,checksum CHAR(64) NOT NULL,applied_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3))");
        Path migrations=root.resolve("deploy/acceptance/migrations");
        try(var paths=Files.list(migrations.resolve("common"))){for(Path file:paths.filter(p->p.getFileName().toString().matches("V.*\\.sql")).sorted().toList())migrate(db,file);}
        for(String file:List.of("tj_learning/V008__learning_motivation.sql","tj_learning/V009__legacy_season_boundary.sql","tj_user/V002__account_security.sql"))migrate(db,migrations.resolve(file));
    }
    private static String ifAbsent(String sql){return sql.startsWith("CREATE TABLE IF NOT EXISTS")?sql:sql.replaceFirst("CREATE TABLE","CREATE TABLE IF NOT EXISTS");}
    private static void migrate(JdbcTemplate db,Path file)throws Exception {
        String version=file.getParent().getFileName()+"/"+file.getFileName();
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        var applied=db.queryForList("SELECT checksum FROM schema_migration WHERE version=?",String.class,version);
        if(!applied.isEmpty()){assertEquals(hash,applied.getFirst(),"Existing test migration changed");return;}
        var sql=new ResourceDatabasePopulator();sql.setSqlScriptEncoding("UTF-8");sql.addScript(new FileSystemResource(file));sql.execute(source);
        db.update("INSERT INTO schema_migration(version,checksum) VALUES(?,?)",version,hash);
    }
    @BeforeEach void prepare()throws Exception {
        UserContext.removeUser();jdbc=new JdbcTemplate(source);json=JsonMapper.builder().findAndAddModules().build();
        teacher=IdWorker.getId();student=IdWorker.getId();author=IdWorker.getId();course=IdWorker.getId();question=IdWorker.getId();reply=IdWorker.getId();
        jdbc.update("INSERT INTO `user`(id,username,cell_phone,password,type,status) VALUES(?,?,?,'synthetic-unusable-password',2,1)",student,"adoption-"+student,String.format("1%010d",Math.floorMod(student,10000000000L)));
        jdbc.update("INSERT INTO user_detail(id,type,name,role_id) VALUES(?,2,'Synthetic adoption learner',2)",student);
        jdbc.update("INSERT INTO interaction_question(id,title,course_id,chapter_id,section_id,user_id) VALUES(?,'Synthetic adoption question',?,?,?,?)",question,course,course,course,author);
        jdbc.update("INSERT INTO interaction_reply(id,question_id,user_id,content) VALUES(?,?,?,'Synthetic answer')",reply,question,student);
        assertNotNull(System.getenv("TJ_INTERNAL_TOKEN"));assertTrue(System.getenv("TJ_INTERNAL_TOKEN").length()>=32);
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new SessionIdentityController(jdbc),new UserController())
                .addInterceptors(new UserInfoInterceptor(),new LoginAuthInterceptor()).setControllerAdvice(new CommonExceptionAdvice()).build();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),8);httpExecutor=Executors.newFixedThreadPool(2);server.setExecutor(httpExecutor);
        server.createContext("/",exchange->{
            try {
                if(exchange.getRequestURI().getPath().startsWith("/internal/"))internalCalls.incrementAndGet();
                if(exchange.getRequestHeaders().getFirst("user-info")!=null)actorHeaders.incrementAndGet();
                if(unavailable.get()) {byte[] bytes="{\"message\":\"injected unavailable identity\"}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(503,bytes.length);exchange.getResponseBody().write(bytes);return;}
                var request=get(exchange.getRequestURI().toString());exchange.getRequestHeaders().forEach((name,values)->request.header(name,values.toArray()));
                var response=mvc.perform(request).andReturn().getResponse();byte[] bytes=response.getContentAsByteArray();exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(response.getStatus(),bytes.length);exchange.getResponseBody().write(bytes);
            }catch(Exception error){throw new java.io.IOException(error);}finally{exchange.close();}
        });server.start();url="http://127.0.0.1:"+server.getAddress().getPort();
        users=Feign.builder().contract(new SpringMvcContract()).requestInterceptor(new RequestIdRelayConfiguration().requestIdInterceptor())
                .retryer(feign.Retryer.NEVER_RETRY).decoder((response,type)->json.readValue(response.body().asInputStream(),(Class<?>)type)).target(UserClient.class,url);
        var manager=new DataSourceTransactionManager(source);var tx=new TransactionTemplate(manager);store=new OperationStore(jdbc,json,tx);
        var clock=Clock.system(ZoneId.of("Asia/Shanghai"));var points=new PointsRecordServiceImpl(jdbc,new InboxStore(jdbc,tx),clock);var outbox=new OutboxStore(jdbc,json);
        var courses=mock(CourseClient.class);var info=new CourseFullInfoDTO();info.setTeacherIds(List.of(teacher));when(courses.getCourseInfoById(course,false,true)).thenReturn(info);
        var handler=new LearningHubOperationHandler(jdbc,json,courses,users,points,null,outbox,clock);
        var beans=new StaticListableBeanFactory();beans.addBean("handler",handler);
        executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(1);executor.setMaxPoolSize(1);executor.setQueueCapacity(10);executor.setThreadNamePrefix("adoption-regression-");executor.initialize();
        worker=new OperationWorker(store,beans.getBeanProvider(OperationHandler.class),executor);
    }
    @AfterEach void cleanup()throws Exception {
        if(executor!=null){executor.shutdown();executor.getThreadPoolExecutor().awaitTermination(10,TimeUnit.SECONDS);}
        if(server!=null)server.stop(0);if(httpExecutor!=null)httpExecutor.shutdownNow();
        for(String operation:operations){jdbc.update("DELETE FROM reliability_operation_failure WHERE operation_id=?",operation);jdbc.update("DELETE FROM reliability_operation WHERE operation_id=?",operation);}
        UserContext.removeUser();
    }
    private LearningHubOperationHandler.Command command(){return new LearningHubOperationHandler.Command("ACCEPT",3L,question,reply,null,null,null,null,null,null,null,null,LocalDateTime.now(ZoneId.of("Asia/Shanghai")));}
    private OperationStore.View execute(LearningHubOperationHandler.Command command,String key)throws Exception {
        assertNull(UserContext.getUser());assertNull(UserContext.getRole());
        var view=store.submit(teacher,"LEARNING_ACCEPT",key,command);if(!operations.contains(view.operationId()))operations.add(view.operationId());
        worker.poll();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(System.nanoTime()<deadline){view=store.get(view.operationId(),teacher);if(!"PENDING".equals(view.status()) || view.errorCode()!=null)return view;Thread.sleep(40);}
        fail("Durable operation did not finish or report a retry within deadline");return null;
    }
    private void noBusinessWrites(){
        assertNull(jdbc.queryForObject("SELECT accepted_reply_id FROM interaction_question WHERE id=?",Long.class,question));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM points_record WHERE user_id=?",Integer.class,student));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM points_daily_quota WHERE user_id=?",Integer.class,student));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM reliability_outbox WHERE business_key=?",Integer.class,"accepted-notice:"+question));
    }
    @Test void workerUsesServiceIdentityOverRealHttpWithoutActorAndAdoptionReplaysAreUnique()throws Exception {
        // Preserve the original auth boundary: a service token alone cannot call the interactive endpoint.
        var old=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url+"/users/"+student)).header("X-Internal-Token",System.getenv("TJ_INTERNAL_TOKEN")).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(401,old.statusCode());
        var cmd=command();String key="adoption-"+question;var first=execute(cmd,key);assertEquals("SUCCEEDED",first.status());
        assertEquals(first.operationId(),execute(cmd,key).operationId());assertEquals("SUCCEEDED",execute(cmd,key+"-new").status());
        assertEquals(reply,jdbc.queryForObject("SELECT accepted_reply_id FROM interaction_question WHERE id=?",Long.class,question));
        assertEquals(teacher,jdbc.queryForObject("SELECT accepted_by FROM interaction_question WHERE id=?",Long.class,question));
        assertEquals(5,jdbc.queryForObject("SELECT SUM(points) FROM points_record WHERE user_id=? AND type=3",Integer.class,student));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM points_record WHERE user_id=?",Integer.class,student));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM reliability_outbox WHERE business_key=?",Integer.class,"accepted-notice:"+question));
        assertEquals(1,internalCalls.get());assertEquals(0,actorHeaders.get());
        executor.submit(()->{assertNull(UserContext.getUser());assertNull(UserContext.getRole());}).get(5,TimeUnit.SECONDS);
    }
    @Test void configurableStudentRoleCannotTurnAStaffAccountIntoAStudentType()throws Exception {
        jdbc.update("UPDATE `user` SET type=1 WHERE id=?",student);jdbc.update("UPDATE user_detail SET type=1,role_id=2 WHERE id=?",student);
        var identity=users.sessionIdentity(student);assertEquals(2L,identity.getRoleId());assertEquals(1,identity.getUserType());
        var result=execute(command(),"not-student-"+question);assertEquals("FAILED",result.status());assertEquals("BUSINESS_FAILED",result.errorCode());noBusinessWrites();
    }
    @Test void disabledAndMissingRecipientsFailWithoutAdoptionOrPoints()throws Exception {
        jdbc.update("UPDATE `user` SET status=0 WHERE id=?",student);var disabled=execute(command(),"disabled-"+question);assertEquals("FAILED",disabled.status());assertEquals("BUSINESS_FAILED",disabled.errorCode());noBusinessWrites();
        jdbc.update("DELETE FROM `user` WHERE id=?",student);var missing=execute(command(),"missing-"+question);assertEquals("FAILED",missing.status());assertEquals("BUSINESS_FAILED",missing.errorCode());noBusinessWrites();
    }
    @Test void unavailableIdentityRemainsRetryableAndDoesNotCommitPartialBusinessState()throws Exception {
        unavailable.set(true);var result=execute(command(),"unavailable-"+question);assertEquals("PENDING",result.status());assertEquals("RETRYING",result.errorCode());noBusinessWrites();
        assertEquals(1,jdbc.queryForObject("SELECT attempts FROM reliability_operation WHERE operation_id=?",Integer.class,result.operationId()));assertEquals(0,actorHeaders.get());
    }
    @Test void realIdentityAuthenticationRejectsMissingTokenButNeedsNoBrowserActor()throws Exception {
        var client=HttpClient.newHttpClient();URI endpoint=URI.create(url+"/internal/v2/users/"+student+"/session-identity");
        assertEquals(403,client.send(HttpRequest.newBuilder(endpoint).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        var valid=client.send(HttpRequest.newBuilder(endpoint).header("X-Internal-Token",System.getenv("TJ_INTERNAL_TOKEN")).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,valid.statusCode());
        var identity=json.readValue(valid.body(),com.tianji.common.domain.dto.LoginUserDTO.class);assertEquals(student,identity.getUserId());assertEquals(2,identity.getUserType());assertNull(UserContext.getUser());
    }
}
