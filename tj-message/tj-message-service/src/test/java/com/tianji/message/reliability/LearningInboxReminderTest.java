package com.tianji.message.reliability;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.common.autoconfigure.mvc.advice.CommonExceptionAdvice;
import com.tianji.common.utils.UserContext;
import com.tianji.message.controller.InternalLearningInboxController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Dedicated real MySQL schema, actual MVC and service-token checks. No production inbox is touched. */
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class LearningInboxReminderTest {
    private JdbcTemplate jdbc;private MockMvc mvc;private long user,other;private LocalDateTime now;private int baseline;private final List<Long> publicIds=new ArrayList<>();
    @BeforeEach void prepare() {
        String port=System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316");
        var admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+port+"/?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true","root",System.getenv("ACCEPTANCE_DB_PASSWORD")));
        admin.execute("CREATE DATABASE IF NOT EXISTS acceptance_message");
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+port+"/acceptance_message?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true","root",System.getenv("ACCEPTANCE_DB_PASSWORD")));
        assertEquals("acceptance_message",jdbc.queryForObject("SELECT DATABASE()",String.class));
        jdbc.execute("CREATE TABLE IF NOT EXISTS user_inbox(id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,type TINYINT DEFAULT 4,title VARCHAR(64) DEFAULT '',content VARCHAR(255) NOT NULL,is_read BIT(1) NOT NULL DEFAULT b'0',publisher BIGINT NOT NULL DEFAULT 0,push_time DATETIME NOT NULL,expire_time DATETIME NOT NULL,public_notice_id BIGINT,UNIQUE KEY uq_user_public_notice(user_id,public_notice_id),KEY(user_id)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE IF NOT EXISTS public_notice(id BIGINT PRIMARY KEY,title VARCHAR(50) NOT NULL,content VARCHAR(255) NOT NULL,type TINYINT NOT NULL,push_time DATETIME NOT NULL,expire_time DATETIME NOT NULL) ENGINE=InnoDB");
        user=IdWorker.getId();other=IdWorker.getId();now=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).withNano(0);UserContext.setUser(user);
        mvc=MockMvcBuilders.standaloneSetup(new InternalLearningInboxController(jdbc),new com.tianji.message.controller.UserInboxController(org.mockito.Mockito.mock(com.tianji.message.service.IUserInboxService.class),jdbc)).setControllerAdvice(new CommonExceptionAdvice()).build();
        assertNotNull(System.getenv("TJ_INTERNAL_TOKEN"));assertTrue(System.getenv("TJ_INTERNAL_TOKEN").length()>=32);
        baseline=readCount();
    }
    @AfterEach void cleanup(){
        jdbc.update("DELETE FROM user_inbox WHERE user_id IN (?,?)",user,other);
        for(long id:publicIds)jdbc.update("DELETE FROM public_notice WHERE id=?",id);
        UserContext.removeUser();
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(){return get("/internal/v1/inboxes/unread-interactions").header("X-Internal-Token",System.getenv("TJ_INTERNAL_TOKEN"));}
    private int readCount(){try{return Integer.parseInt(mvc.perform(request()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}catch(Exception error){throw new AssertionError(error);}}
    private long inbox(long actor,int type,boolean read,LocalDateTime push,LocalDateTime expiry,Long publicId){long id=IdWorker.getId();jdbc.update("INSERT INTO user_inbox(id,user_id,type,title,content,is_read,push_time,expire_time,public_notice_id) VALUES(?,?,?,'interaction','content',?,?,?,?)",id,actor,type,read,push,expiry,publicId);return id;}
    private long notice(int type,LocalDateTime push,LocalDateTime expiry){long id=IdWorker.getId();jdbc.update("INSERT INTO public_notice(id,title,content,type,push_time,expire_time) VALUES(?,'interaction','content',?,?,?)",id,type,push,expiry);publicIds.add(id);return id;}
    @Test void allOwnUnreadInteractionsCountBeyondOneHundredAndReadingChangesCount()throws Exception {
        long first=0;for(int i=0;i<121;i++){long id=inbox(user,2,false,now.minusDays(1),now.plusDays(1),null);if(i==0)first=id;}
        inbox(other,2,false,now.minusDays(1),now.plusDays(1),null);inbox(user,2,true,now.minusDays(1),now.plusDays(1),null);inbox(user,2,false,now.minusDays(2),now.minusDays(1),null);inbox(user,2,false,now.plusDays(1),now.plusDays(2),null);inbox(user,4,false,now.minusDays(1),now.plusDays(1),null);
        mvc.perform(request().param("userId",Long.toString(other))).andExpect(status().isOk()).andExpect(content().string(Integer.toString(baseline+121)));
        mvc.perform(put("/inboxes/"+first+"/read")).andExpect(status().isOk());assertEquals(baseline+120,readCount());
        long otherMessage=inbox(other,2,false,now.minusDays(1),now.plusDays(1),null);mvc.perform(put("/inboxes/"+otherMessage+"/read")).andExpect(status().isBadRequest());assertEquals(0,jdbc.queryForObject("SELECT is_read+0 FROM user_inbox WHERE id=?",Integer.class,otherMessage));
    }
    @Test void unmaterializedPublicInteractionsHaveNoPageCapAndReadReceiptsNeverDoubleCount(){
        long first=0;for(int i=0;i<121;i++){long id=notice(2,now.minusDays(1),now.plusDays(1));if(i==0)first=id;}
        notice(2,now.minusDays(2),now.minusDays(1));notice(2,now.plusDays(1),now.plusDays(2));notice(0,now.minusDays(1),now.plusDays(1));
        assertEquals(baseline+121,readCount());long row=inbox(user,2,false,now.minusDays(1),now.plusDays(1),first);assertEquals(baseline+121,readCount());
        jdbc.update("UPDATE user_inbox SET is_read=1 WHERE id=?",row);assertEquals(baseline+120,readCount());
    }
    @Test void noUntrustedServiceOrMissingActorCanReadTheCount()throws Exception {
        mvc.perform(get("/internal/v1/inboxes/unread-interactions")).andExpect(status().isForbidden());
        UserContext.removeUser();mvc.perform(request()).andExpect(status().isUnauthorized());
    }
}
