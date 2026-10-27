package com.tianji.auth.security;

import com.tianji.auth.domain.po.LoginRecord;
import com.tianji.auth.mapper.LoginRecordMapper;
import com.tianji.auth.service.impl.LoginRecordServiceImpl;
import com.tianji.common.autoconfigure.reliability.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class LoginRecordDeliveryDatabaseTest {
    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    @org.springframework.boot.autoconfigure.ImportAutoConfiguration({
        com.tianji.common.autoconfigure.mybatis.MybatisConfig.class,
        com.baomidou.mybatisplus.autoconfigure.MybatisPlusInnerInterceptorAutoConfiguration.class,
        com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class})
    static class FactoryConfiguration {}

    private JdbcTemplate jdbc;private OperationStore operations;private LoginRecordServiceImpl service;
    private AnnotationConfigApplicationContext context;private LoginRecordMapper actualMapper,mapper;
    private final JsonMapper json=JsonMapper.builder().build();
    private final long owner=ThreadLocalRandom.current().nextLong(1,Long.MAX_VALUE);

    @BeforeEach void setup()throws Exception {
        String base="jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/";
        String options="?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&connectTimeout=3000&socketTimeout=5000";
        String password=System.getenv("ACCEPTANCE_DB_PASSWORD");
        new JdbcTemplate(new DriverManagerDataSource(base+options,"root",password)).execute("CREATE DATABASE IF NOT EXISTS acceptance_login_records");
        var source=new DriverManagerDataSource(base+"acceptance_login_records"+options,"root",password);jdbc=new JdbcTemplate(source);
        assertEquals("acceptance_login_records",jdbc.queryForObject("SELECT DATABASE()",String.class));
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while(root!=null&&!Files.isRegularFile(root.resolve("deploy/compact/schema.sql")))root=root.getParent();assertNotNull(root);
        var definition=Pattern.compile("CREATE TABLE `login_record` \\(.*?\\) ENGINE=.*?;",Pattern.DOTALL)
            .matcher(Files.readString(root.resolve("deploy/compact/schema.sql")));
        assertTrue(definition.find());jdbc.execute(definition.group().replaceFirst("CREATE TABLE","CREATE TABLE IF NOT EXISTS"));
        try(var migrations=Files.list(root.resolve("deploy/acceptance/migrations/common"))) {
            for(var migration:migrations.filter(p->p.toString().endsWith(".sql")).sorted().toList()) {
                if(migration.getFileName().toString().startsWith("V005") && jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='admin_audit' AND column_name='operation_id'",Integer.class)>0)continue;
                for(String statement:Files.readString(migration).split(";"))if(!statement.isBlank())jdbc.execute(statement);
            }
        }
        operations=new OperationStore(jdbc,json,new TransactionTemplate(new DataSourceTransactionManager(source)));
        context=new AnnotationConfigApplicationContext();
        context.registerBean("dataSource",javax.sql.DataSource.class,()->source);
        context.register(FactoryConfiguration.class);context.refresh();
        var factory=context.getBean(SqlSessionFactory.class);if(!factory.getConfiguration().hasMapper(LoginRecordMapper.class))factory.getConfiguration().addMapper(LoginRecordMapper.class);
        actualMapper=new SqlSessionTemplate(factory).getMapper(LoginRecordMapper.class);mapper=mock(LoginRecordMapper.class);
        doAnswer(call->actualMapper.insert((LoginRecord)call.getArgument(0))).when(mapper).insert(any(LoginRecord.class));
        service=service();
    }
    private LoginRecordServiceImpl service() {
        var service=new LoginRecordServiceImpl(operations,json);ReflectionTestUtils.setField(service,"baseMapper",mapper);return service;
    }
    private LoginRecord record() {
        var record=new LoginRecord();record.setUserId(owner);record.setCellPhone("13800010001");record.setIpv4("127.0.0.1");
        record.setLoginTime(LocalDateTime.now().withNano(0));record.setLoginDate(record.getLoginTime().toLocalDate());return record;
    }
    private int records(){return jdbc.queryForObject("SELECT COUNT(*) FROM login_record WHERE user_id=?",Integer.class,owner);}
    private OperationStore.Work work() {
        return operations.due(service.kind(),100).stream().filter(w->w.userId()==owner).findFirst().orElseThrow();
    }
    private AnnotationConfigApplicationContext managedPool(int queue) {
        var application=new AnnotationConfigApplicationContext();
        application.registerBean("operationExecutor",ThreadPoolTaskExecutor.class,()->{
            var environment=new org.springframework.mock.env.MockEnvironment()
                .withProperty("tj.reliability.operation-core","1").withProperty("tj.reliability.operation-max","1");
            var pool=new ReliabilityConfiguration().operationExecutor(environment);
            pool.setQueueCapacity(queue);pool.setAwaitTerminationSeconds(5);return pool;
        });
        application.refresh();return application;
    }
    private OperationWorker worker(ThreadPoolTaskExecutor executor,LoginRecordServiceImpl handler) {
        var beans=new org.springframework.beans.factory.support.StaticListableBeanFactory();beans.addBean("loginRecords",handler);
        return new OperationWorker(operations,beans.getBeanProvider(OperationHandler.class),executor);
    }
    @AfterEach void cleanup() {
        if(context!=null)context.close();if(jdbc!=null){
            jdbc.update("DELETE f FROM reliability_operation_failure f JOIN reliability_operation o ON f.operation_id=o.operation_id WHERE o.user_id=?",owner);
            jdbc.update("DELETE FROM reliability_operation WHERE user_id=?",owner);jdbc.update("DELETE FROM login_record WHERE user_id=?",owner);
        }
    }

    @Test void saturatedExecutorKeepsAcceptedRecordsDurableAndANewWorkerDrainsThem()throws Exception {
        for(int n=0;n<3;n++)service.saveAsync(record());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM reliability_operation WHERE user_id=? AND status='PENDING'",Integer.class,owner));
        var managed=managedPool(1);var executor=managed.getBean(ThreadPoolTaskExecutor.class);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        executor.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException stopped){Thread.currentThread().interrupt();}});
        try {
            assertTrue(entered.await(3,TimeUnit.SECONDS));worker(executor,service).poll();
            assertEquals(1,executor.getThreadPoolExecutor().getQueue().size());assertEquals(0,records());
            // Complete the single admitted task; the two non-admitted records
            // remain durable without relying on an in-memory logging queue.
            Objects.requireNonNull(executor.getThreadPoolExecutor().getQueue().poll()).run();
            assertEquals(1,records());
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM reliability_operation WHERE user_id=? AND status='PENDING'",Integer.class,owner));
        } finally {release.countDown();managed.close();}
        assertTrue(executor.getThreadPoolExecutor().isShutdown());
        var restarted=managedPool(2);var restartedExecutor=restarted.getBean(ThreadPoolTaskExecutor.class);
        try {worker(restartedExecutor,service()).poll();}finally{restarted.close();}
        assertEquals(3,records());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM reliability_operation WHERE user_id=? AND status='SUCCEEDED'",Integer.class,owner));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM login_record WHERE user_id=? AND cell_phone='13800010001' AND ipv4='127.0.0.1'",Integer.class,owner));
    }

    @Test void anInsertFailureRollsBackBothSidesAndARetryCannotDuplicateTheRecord() {
        var fail=new AtomicBoolean(true);
        doAnswer(call->{
            int changed=actualMapper.insert((LoginRecord)call.getArgument(0));
            if(fail.getAndSet(false))throw new TransientDataAccessResourceException("injected failure after insert");return changed;
        }).when(mapper).insert(any(LoginRecord.class));
        service.saveAsync(record());var first=work();assertTrue(operations.claim(first.id(),"first"));operations.execute(first,"first",service);
        assertEquals(0,records());assertEquals("RETRYING",operations.get(first.id(),owner).errorCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM reliability_operation_failure WHERE operation_id=?",Integer.class,first.id()));
        jdbc.update("UPDATE reliability_operation SET next_attempt_at=NOW(3) WHERE operation_id=?",first.id());
        var retry=work();assertTrue(operations.claim(retry.id(),"retry"));operations.execute(retry,"retry",service);
        operations.execute(retry,"retry",service);operations.execute(first,"first",service);
        assertEquals(1,records());assertEquals("SUCCEEDED",operations.get(first.id(),owner).status());
    }

    @Test void exhaustedPersistenceFailuresRemainVisibleAndReplayOnlyOnce() {
        doThrow(new TransientDataAccessResourceException("injected persistent failure")).when(mapper).insert(any(LoginRecord.class));
        service.saveAsync(record());var admitted=work();jdbc.update("UPDATE reliability_operation SET attempts=9 WHERE operation_id=?",admitted.id());
        var last=work();assertTrue(operations.claim(last.id(),"last"));operations.execute(last,"last",service);
        assertEquals(0,records());assertEquals("RETRY_EXHAUSTED",operations.get(last.id(),owner).errorCode());
        var failure=operations.failures().stream().filter(row->row.get("operation_id").equals(last.id())).findFirst().orElseThrow();
        long version=((Number)failure.get("version")).longValue();operations.replayFailure(last.id(),version);
        doAnswer(call->actualMapper.insert((LoginRecord)call.getArgument(0))).when(mapper).insert(any(LoginRecord.class));
        var retry=work();assertTrue(operations.claim(retry.id(),"replay"));operations.execute(retry,"replay",service);
        operations.execute(retry,"replay",service);assertEquals(1,records());assertEquals("SUCCEEDED",operations.get(last.id(),owner).status());
    }
}
