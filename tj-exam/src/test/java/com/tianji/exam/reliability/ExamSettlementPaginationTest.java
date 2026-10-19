package com.tianji.exam.reliability;

import com.tianji.api.dto.exam.ExamSettlementPage;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.InternalAuth;
import com.tianji.exam.controller.InternalExamController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/** Real MySQL keyset scans; all synthetic facts roll back in acceptance_exam. */
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class ExamSettlementPaginationTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private InternalExamController controller;
    private YearMonth month;
    private String prefix;

    @BeforeEach void setup() {
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_exam?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&rewriteBatchedStatements=true","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
        jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        assertEquals("acceptance_exam",jdbc.queryForObject("SELECT DATABASE()",String.class));
        controller=new InternalExamController(jdbc);prefix=UUID.randomUUID().toString();
        month=YearMonth.of(8000+ThreadLocalRandom.current().nextInt(1000),1+ThreadLocalRandom.current().nextInt(12));
    }
    private String value(){return month.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));}
    private void events(int count,int pendingIndex) {
        jdbc.batchUpdate("INSERT INTO reliability_outbox(event_id,business_key,exchange_name,routing_key,event_type,schema_version,payload,status,next_attempt_at,created_at) VALUES(?,?,'learning.topic','exam.passed','exam.passed',1,'{}',?,?,?)",
                IntStream.range(0,count).boxed().toList(),1000,(statement,index)->{
                    statement.setString(1,UUID.randomUUID().toString());statement.setString(2,prefix+":"+index+":"+count);
                    statement.setString(3,index==pendingIndex?"PENDING":"SENT");
                    statement.setObject(4,month.atDay(3).atStartOfDay());statement.setObject(5,month.atDay(3).atStartOfDay());
                });
    }
    private void verifyCount(int count) {
        tx.executeWithoutResult(status->{
            status.setRollbackOnly();events(count,-1);
            try(var trusted=mockStatic(InternalAuth.class)) {
                var ids=new HashSet<String>();long cursor=0;Long upper=null;int pages=0;
                do {
                    var page=controller.readiness(value(),cursor,upper);pages++;
                    assertEquals(value(),page.month());assertEquals(cursor,page.cursor());
                    assertTrue(page.eventIds().size()<=ExamSettlementPage.PAGE_SIZE);assertEquals(0,page.pending());
                    if(upper==null)upper=page.upperBound();else assertEquals(upper.longValue(),page.upperBound());
                    for(String id:page.eventIds())assertTrue(ids.add(id),"An event appeared on more than one page");
                    if(page.nextCursor()==null)break;
                    assertTrue(page.nextCursor()>cursor);cursor=page.nextCursor();
                } while(true);
                assertEquals(count,ids.size());assertEquals((count+999)/1000,pages);
            }
        });
    }
    @Test void exactlyTenThousandEventsFinishWithoutAnArtificialBlock(){verifyCount(10000);}
    @Test void tenThousandAndOneEventsFinishAcrossElevenBoundedPages(){verifyCount(10001);}
    @Test void upperBoundExcludesLaterInsertsAndPendingIsReportedOnItsOwnPage() {
        tx.executeWithoutResult(status->{
            status.setRollbackOnly();events(1001,1000);
            try(var trusted=mockStatic(InternalAuth.class)) {
                var first=controller.readiness(value(),0,null);assertEquals(1000,first.eventIds().size());assertEquals(0,first.pending());assertNotNull(first.nextCursor());
                events(1,-1);
                var last=controller.readiness(value(),first.nextCursor(),first.upperBound());
                assertEquals(1,last.eventIds().size());assertEquals(1,last.pending());assertNull(last.nextCursor());
                var fresh=controller.readiness(value(),0,null);
                assertTrue(fresh.upperBound()>first.upperBound(),"A fresh scan includes newly committed sequence values");
            }
        });
    }
    @Test void malformedMonthAndUnboundOrRegressingCursorsAreRejected() {
        try(var trusted=mockStatic(InternalAuth.class)) {
            assertThrows(BadRequestException.class,()->controller.readiness("202613",0,null));
            assertThrows(BadRequestException.class,()->controller.readiness("202610",1,null));
            assertThrows(BadRequestException.class,()->controller.readiness("202610",-1,10L));
            assertThrows(BadRequestException.class,()->controller.readiness("202610",11,10L));
        }
    }
}
