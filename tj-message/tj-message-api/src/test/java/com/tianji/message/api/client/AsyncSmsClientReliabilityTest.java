package com.tianji.message.api.client;

import com.tianji.common.autoconfigure.reliability.OutboxStore;
import com.tianji.message.domain.dto.SmsInfoDTO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class AsyncSmsClientReliabilityTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private AsyncSmsClient client;
    @BeforeEach void setup() {
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:23316/acceptance_common?serverTimezone=Asia/Shanghai","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
        jdbc=new JdbcTemplate(source);
        var manager=new DataSourceTransactionManager(source);
        tx=new TransactionTemplate(manager);
        client=new AsyncSmsClient(new OutboxStore(jdbc,JsonMapper.builder().build()),manager);
        jdbc.update("DELETE FROM reliability_outbox");
        jdbc.update("UPDATE test_counter SET value=0 WHERE id=1");
    }
    private SmsInfoDTO message() {
        var form=new SmsInfoDTO();form.setTemplateCode("VERIFY_CODE");
        form.setPhones(List.of("acceptance-only"));
        form.setTemplateParams(Map.of("code","0000"));return form;
    }
    @Test void acceptedMessageIsDurableWithoutAnyBrokerConnection() {
        client.sendMessage(message());
        var row=jdbc.queryForMap("SELECT event_id,payload,status FROM reliability_outbox");
        assertEquals("PENDING",row.get("status"));
        var envelope=JsonMapper.builder().build().readTree((String)row.get("payload"));
        assertEquals(row.get("event_id"),envelope.get("eventId").asString());
        assertEquals("0000",envelope.get("payload").get("templateParams").get("code").asString());
    }
    @Test void callerRollbackAlsoRollsBackSmsIntent() {
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{
            jdbc.update("UPDATE test_counter SET value=value+1 WHERE id=1");
            client.sendMessage(message());throw new IllegalStateException("rollback");
        }));
        assertEquals(0,jdbc.queryForObject("SELECT value FROM test_counter WHERE id=1",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM reliability_outbox",Integer.class));
    }
}
