package com.tianji.compact;

import com.tianji.user.constants.UserConstants;
import com.tianji.user.service.impl.TemporaryCredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.boot.health.contributor.Status;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Real user runner through the compact host entrypoint, using an isolated synthetic schema. */
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class CompactCredentialStartupDatabaseTest {
    JdbcTemplate jdbc;DriverManagerDataSource source;ModuleRegistry registry;
    BCryptPasswordEncoder passwords=new BCryptPasswordEncoder();
    @BeforeEach void database(){
        String port=System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316"),password=System.getenv("ACCEPTANCE_DB_PASSWORD");
        String database="acceptance_startup_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        var admin=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+port+"/?allowPublicKeyRetrieval=true&useSSL=false","root",password);
        new JdbcTemplate(admin).execute("CREATE DATABASE `"+database+"`");
        source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+port+"/"+database+"?allowPublicKeyRetrieval=true&useSSL=false","root",password);jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE `user`(id BIGINT PRIMARY KEY,password VARCHAR(255) NOT NULL,must_change_password TINYINT NOT NULL DEFAULT 0,temporary_password_expires_at DATETIME(3)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE user_password_upgrade_guard(id INT PRIMARY KEY,completed TINYINT NOT NULL DEFAULT 0) ENGINE=InnoDB");
        jdbc.update("INSERT INTO user_password_upgrade_guard VALUES(1,0)");registry=new CompactHost().moduleRegistry();
    }
    @AfterEach void close(){if(registry!=null)registry.close();}
    private GenericWebApplicationContext child(){
        var child=new GenericWebApplicationContext();child.registerBean("credentials",TemporaryCredentialService.class,
            ()->new TemporaryCredentialService(jdbc,passwords,new DataSourceTransactionManager(source)));child.refresh();
        registry.add("user",child,new DispatcherServlet(child));return child;
    }
    @Test void realChildStartupDisablesKnownDefaultPreservesCustomPasswordAndIsIdempotent() throws Exception {
        String custom=passwords.encode("synthetic-custom-passphrase");
        jdbc.update("INSERT INTO `user`(id,password) VALUES(1,?),(2,?)",passwords.encode(UserConstants.DEFAULT_PASSWORD),custom);child();
        assertEquals(0,jdbc.queryForObject("SELECT completed FROM user_password_upgrade_guard WHERE id=1",Integer.class));assertEquals(Status.DOWN,registry.health().getStatus());
        var runner=new CompactHost().moduleStartupTasks(registry);runner.run(new DefaultApplicationArguments());
        assertEquals(1,jdbc.queryForObject("SELECT completed FROM user_password_upgrade_guard WHERE id=1",Integer.class));
        String disabled=jdbc.queryForObject("SELECT password FROM `user` WHERE id=1",String.class);
        assertFalse(passwords.matches(UserConstants.DEFAULT_PASSWORD,disabled));assertEquals(1,jdbc.queryForObject("SELECT must_change_password FROM `user` WHERE id=1",Integer.class));
        assertEquals(custom,jdbc.queryForObject("SELECT password FROM `user` WHERE id=2",String.class));
        runner.run(new DefaultApplicationArguments());assertEquals(disabled,jdbc.queryForObject("SELECT password FROM `user` WHERE id=1",String.class));assertEquals(Status.UP,registry.health().getStatus());
    }
    @Test void missingUpgradeGuardFailsStartupAndClosesTheChild(){
        jdbc.update("DELETE FROM user_password_upgrade_guard");var child=child();
        assertThrows(Exception.class,()->new CompactHost().moduleStartupTasks(registry).run(new DefaultApplicationArguments()));
        assertFalse(child.isActive());assertEquals(Status.DOWN,registry.health().getStatus());
    }
}
