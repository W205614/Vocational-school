package com.tianji.common.reliability;
import com.tianji.common.autoconfigure.reliability.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class ReliabilityMetricsTest {
 @Test void intentionalDelayIsNotOverdueAndLeaseRecoveryDoesNotHideRealDelay(){
  var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_common?serverTimezone=Asia/Shanghai","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
  var jdbc=new JdbcTemplate(source);jdbc.update("DELETE FROM reliability_outbox");
  String id=java.util.UUID.randomUUID().toString();
  jdbc.update("INSERT INTO reliability_outbox(event_id,business_key,exchange_name,routing_key,event_type,payload,delay_ms,status,created_at,next_attempt_at) VALUES(?,?,'test','test','test','{}',300000,'PENDING',NOW(3)-INTERVAL 120 SECOND,NOW(3)+INTERVAL 180 SECOND)",id,id);
  var meters=new SimpleMeterRegistry();var metrics=new ReliabilityMetrics(jdbc,meters);metrics.refresh();
  assertEquals(0,meters.get("tj.events.oldest.seconds").gauge().value());
  assertEquals(1,meters.get("tj.events.pending").gauge().value());
  jdbc.update("UPDATE reliability_outbox SET delay_ms=0,status='SENDING',next_attempt_at=NOW(3)+INTERVAL 30 SECOND WHERE event_id=?",id);
  metrics.refresh();assertTrue(meters.get("tj.events.oldest.seconds").gauge().value()>=120);
  jdbc.update("UPDATE reliability_outbox SET status='SENT' WHERE event_id=?",id);
  metrics.refresh();assertEquals(0,meters.get("tj.events.oldest.seconds").gauge().value());
  meters.close();
 }
 @Test void exhaustedBusinessOperationsAreVisibleAndFailedRefreshDoesNotPretendFreshness(){
  var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_common?serverTimezone=Asia/Shanghai","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
  boolean[] fail={false};
  var jdbc=new JdbcTemplate(source){@Override public java.util.Map<String,Object> queryForMap(String sql){if(fail[0] && sql.contains("reliability_operation"))throw new org.springframework.dao.DataAccessResourceFailureException("injected collection failure");return super.queryForMap(sql);}};
  jdbc.update("DELETE FROM reliability_operation_failure");jdbc.update("DELETE FROM reliability_operation");
  String prefix=java.util.UUID.randomUUID().toString();
  for(int i=0;i<3;i++)jdbc.update("INSERT INTO reliability_operation(operation_id,user_id,kind,idempotency_key,request_hash,payload,status,error_code,created_at) VALUES(?,1,'METRICS',?,'hash','{}',?,?,NOW(3)-INTERVAL 120 SECOND)",prefix+i,prefix+i,i==0?"PENDING":"FAILED",i==1?"RETRY_EXHAUSTED":i==2?"BUSINESS_FAILED":null);
  var meters=new SimpleMeterRegistry();try{
   var metrics=new ReliabilityMetrics(jdbc,meters);metrics.refresh();
   assertEquals(1,meters.get("tj.operations.retry.exhausted").gauge().value());
   assertEquals(1,meters.get("tj.operations.pending").gauge().value());
   assertTrue(meters.get("tj.operations.oldest.seconds").gauge().value()>=120);
   double success=meters.get("tj.reliability.metrics.last.success.timestamp.seconds").gauge().value();assertTrue(success>0);
   jdbc.update("UPDATE reliability_operation SET status='SUCCEEDED' WHERE operation_id=?",prefix+"0");
   fail[0]=true;assertThrows(org.springframework.dao.DataAccessException.class,metrics::refresh);
   assertEquals(success,meters.get("tj.reliability.metrics.last.success.timestamp.seconds").gauge().value());
   assertEquals(1,meters.get("tj.operations.pending").gauge().value());
   fail[0]=false;metrics.refresh();assertEquals(0,meters.get("tj.operations.oldest.seconds").gauge().value());
  } finally {meters.close();jdbc.update("DELETE FROM reliability_operation WHERE kind='METRICS'");}
 }
}
