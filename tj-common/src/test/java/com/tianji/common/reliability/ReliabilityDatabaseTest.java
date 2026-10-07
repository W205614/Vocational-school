package com.tianji.common.reliability;
import com.tianji.common.autoconfigure.reliability.*;
import com.tianji.common.autoconfigure.mq.*;
import com.tianji.common.exceptions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.amqp.rabbit.connection.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ACCEPTANCE_DB_PASSWORD",matches=".+")
class ReliabilityDatabaseTest {
 private JdbcTemplate jdbc;private TransactionTemplate tx;private OutboxStore outbox;private InboxStore inbox;private OperationStore operations;
 @BeforeEach void setup() {
  var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+System.getenv().getOrDefault("ACCEPTANCE_DB_PORT","23316")+"/acceptance_common?serverTimezone=Asia/Shanghai","root",System.getenv("ACCEPTANCE_DB_PASSWORD"));
  jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));
  var json=JsonMapper.builder().build();outbox=new OutboxStore(jdbc,json);inbox=new InboxStore(jdbc,tx);operations=new OperationStore(jdbc,json,tx);
  jdbc.update("DELETE FROM reliability_operation_failure");jdbc.update("DELETE FROM reliability_inbox");jdbc.update("DELETE FROM reliability_outbox");jdbc.update("DELETE FROM reliability_operation");
  jdbc.update("UPDATE test_counter SET value=0 WHERE id=1");
 }
 @Test void eventAndBusinessCommitOrRollbackTogether() {
  assertThrows(IllegalStateException.class,()->outbox.enqueue("outside","test","event",Map.of("value",1)));
  assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(s->{
   jdbc.update("UPDATE test_counter SET value=1 WHERE id=1");outbox.enqueue("rollback","test","event",Map.of("value",1));throw new IllegalArgumentException();
  }));
  assertEquals(0,count("reliability_outbox"));assertEquals(0,value());
  tx.executeWithoutResult(s->{jdbc.update("UPDATE test_counter SET value=1 WHERE id=1");outbox.enqueue("commit","test","event",Map.of("value",1));});
  assertEquals(1,count("reliability_outbox"));assertEquals(1,value());
 }
 @Test void concurrentDeliveryAppliesOnlyOnce() throws Exception {
  try(var executor=Executors.newFixedThreadPool(20)) {
   var futures=new ArrayList<Future<?>>();
   for(int i=0;i<100;i++) futures.add(executor.submit(()->inbox.once("counter","stable",()->jdbc.update("UPDATE test_counter SET value=value+1 WHERE id=1"))));
   for(var future:futures) future.get(30,TimeUnit.SECONDS);
  }
  assertEquals(1,value());assertEquals(1,count("reliability_inbox"));
 }
 @Test void failedConsumerTransactionCanBeRetried() {
  assertThrows(IllegalArgumentException.class,()->inbox.once("counter","failed",()->{jdbc.update("UPDATE test_counter SET value=value+1 WHERE id=1");throw new IllegalArgumentException();}));
  assertEquals(0,value());assertEquals(0,count("reliability_inbox"));
  assertTrue(inbox.once("counter","failed",()->jdbc.update("UPDATE test_counter SET value=value+1 WHERE id=1")));
  assertEquals(1,value());
 }
 @Test void staleLeaseCannotCompleteAnotherWorkersEvent() {
  String id=tx.execute(s->outbox.enqueue("lease","test","event",Map.of("value",1)));
  assertTrue(outbox.claim(id,"first"));assertFalse(outbox.claim(id,"second"));
  jdbc.update("UPDATE reliability_outbox SET next_attempt_at=NOW() WHERE event_id=?",id);
  assertTrue(outbox.claim(id,"second"));
  outbox.sent(id,"first");assertEquals("SENDING",status(id));
  outbox.sent(id,"second");assertEquals("SENT",status(id));
 }
 @Test void sameRequestReturnsSameOperationAndDifferentPayloadConflicts() throws Exception {
  var ids=ConcurrentHashMap.<String>newKeySet();
  try(var executor=Executors.newFixedThreadPool(10)) {
   List<Future<?>> futures=new ArrayList<>();
   for(int i=0;i<50;i++) futures.add(executor.submit(()->ids.add(operations.submit(1,"TEST","request",Map.of("amount",1)).operationId())));
   for(var f:futures) f.get(30,TimeUnit.SECONDS);
  }
  assertEquals(1,ids.size());assertEquals(1,count("reliability_operation"));
  assertThrows(ConflictException.class,()->operations.submit(1,"TEST","request",Map.of("amount",2)));
  assertThrows(BadRequestException.class,()->operations.get(ids.iterator().next(),2));
 }
 @Test void exhaustedOperationCanReplayOriginalIdentityButNotBusinessFailure(){
  var op=operations.submit(1,"TEST","exhausted",Map.of());jdbc.update("UPDATE reliability_operation SET attempts=9 WHERE operation_id=?",op.operationId());
  var work=operations.due("TEST",1).getFirst();assertTrue(operations.claim(work.id(),"worker"));
  operations.execute(work,"worker",new OperationHandler(){public String kind(){return "TEST";} public Object execute(String id,long user,String payload){throw new IllegalStateException("injected temporary failure");}});
  assertEquals("RETRY_EXHAUSTED",operations.get(op.operationId(),1).errorCode());
  assertFalse(operations.get(op.operationId(),1).errorMessage().contains("injected"));
  var row=operations.failures().getFirst();long version=((Number)row.get("version")).longValue();
  operations.replayFailure(op.operationId(),version);assertEquals("PENDING",operations.get(op.operationId(),1).status());
  assertThrows(ConflictException.class,()->operations.replayFailure(op.operationId(),version));
  var rejected=operations.submit(1,"TEST","business",Map.of());
  var businessWork=operations.due("TEST",10).stream().filter(w->w.id().equals(rejected.operationId())).findFirst().orElseThrow();
  assertTrue(operations.claim(businessWork.id(),"business"));
  operations.execute(businessWork,"business",new OperationHandler(){public String kind(){return "TEST";}public Object execute(String id,long user,String payload){throw new BadRequestException("invalid request");}});
  long rejectedVersion=operations.failures().stream().filter(r->r.get("operation_id").equals(rejected.operationId())).map(r->((Number)r.get("version")).longValue()).findFirst().orElseThrow();
  assertThrows(ConflictException.class,()->operations.replayFailure(rejected.operationId(),rejectedVersion));
 }
 @Test void rejectedQueuedWorkReleasesItsLeaseWithoutConsumingAnAttempt(){
  var op=operations.submit(1,"TEST","rejection",Map.of());
  assertTrue(operations.claim(op.operationId(),"old"));
  operations.releaseUnstarted(op.operationId(),"old");
  var work=operations.due("TEST",1).getFirst();assertEquals(0,work.attempts());
  assertTrue(operations.claim(work.id(),"new"));
  operations.releaseUnstarted(work.id(),"old");assertTrue(operations.due("TEST",1).isEmpty());
 }
 @Test void reorderedAnswerObjectsKeepRequestIdentity() {
  var first=new LinkedHashMap<String,Object>();first.put("question2",List.of("A","B"));first.put("question1","answer");
  var second=new LinkedHashMap<String,Object>();second.put("question1","answer");second.put("question2",List.of("A","B"));
  assertEquals(operations.submit(1,"TEST","answers",Map.of("answers",first)).operationId(),operations.submit(1,"TEST","answers",Map.of("answers",second)).operationId());
  second.put("question1","changed");assertThrows(ConflictException.class,()->operations.submit(1,"TEST","answers",Map.of("answers",second)));
 }
 @Test void retryRetainsTheFirstServerSnapshotEvenIfMetadataChanges(){
  var first=operations.submit(1,"TEST","snapshot",Map.of("course",1),Map.of("sectionCount",2));
  assertEquals(first.operationId(),operations.submit(1,"TEST","snapshot",Map.of("course",1),Map.of("sectionCount",3)).operationId());
  assertEquals(2,((Number)JsonMapper.builder().build().readValue(operations.due("TEST",1).getFirst().payload(),Map.class).get("sectionCount")).intValue());
  assertThrows(ConflictException.class,()->operations.submit(1,"TEST","snapshot",Map.of("course",2),Map.of("sectionCount",2)));
 }
 @Test void operationRollbackPreservesFailureAndAllowsNoPartialSuccess() {
  var view=operations.submit(1,"TEST","failure",Map.of("amount",1));var work=operations.due(1).getFirst();
  assertTrue(operations.claim(work.id(),"worker"));
  operations.execute(work,"worker",new OperationHandler(){
   public String kind(){return "TEST";}
   public Object execute(String id,long user,String payload){jdbc.update("UPDATE test_counter SET value=value+1 WHERE id=1");throw new BadRequestException("business rejection");}
  });
  assertEquals(0,value());assertEquals("FAILED",operations.get(view.operationId(),1).status());
 }
 @Test void brokerDuplicateDeliveryAfterConsumerCommitRemainsIdempotent() {
  var connection=new CachingConnectionFactory("127.0.0.1",Integer.parseInt(System.getenv().getOrDefault("ACCEPTANCE_MQ_PORT","23373")));
  connection.setUsername(System.getenv().getOrDefault("ACCEPTANCE_MQ_USERNAME","acceptance"));connection.setPassword(System.getenv("ACCEPTANCE_MQ_PASSWORD"));
  var template=new RabbitTemplate(connection);template.setMessageConverter(new EnvelopeJsonMessageConverter(JsonMapper.builder().build()));
  RabbitMqHelper helper=new MqConfig().rabbitMqHelper(template);
  var admin=new RabbitAdmin(connection);
  String name="acceptance."+UUID.randomUUID();
  admin.declareExchange(new DirectExchange(name,true,false));admin.declareQueue(new org.springframework.amqp.core.Queue(name,true,false,false));
  admin.declareBinding(new Binding(name,Binding.DestinationType.QUEUE,name,"event",null));
  try {
   String id=tx.execute(s->outbox.enqueue("broker",name,"event",Map.of("value",1)));
   var event=outbox.candidates(1).getFirst();
   for(int i=0;i<2;i++) helper.sendStored(name,"event",event.payload(),id,event.businessKey(),event.type(),1,0);
   for(int i=0;i<2;i++) {
    Message message=template.receive(name,5000);assertNotNull(message);assertEquals(id,message.getMessageProperties().getMessageId());
    inbox.once("broker.counter",id,()->jdbc.update("UPDATE test_counter SET value=value+1 WHERE id=1"));
   }
   assertEquals(1,value());
   assertThrows(IllegalStateException.class,()->helper.sendStored(name,"missing",event.payload(),id,event.businessKey(),event.type(),1,0));
  } finally {admin.deleteQueue(name);admin.deleteExchange(name);helper.destroy();connection.destroy();}
 }
 @Test void failedConsumerCanReplayOriginalIdentityAndRejectStaleVersion() {
  var connection=new CachingConnectionFactory("127.0.0.1",Integer.parseInt(System.getenv().getOrDefault("ACCEPTANCE_MQ_PORT","23373")));
  connection.setUsername(System.getenv().getOrDefault("ACCEPTANCE_MQ_USERNAME","acceptance"));connection.setPassword(System.getenv("ACCEPTANCE_MQ_PASSWORD"));
  connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);connection.setPublisherReturns(true);
  var template=new RabbitTemplate(connection);template.setMandatory(true);
  var admin=new RabbitAdmin(connection);String queue="acceptance.replay."+UUID.randomUUID();
  admin.declareQueue(new org.springframework.amqp.core.Queue(queue,true,false,false));
  var store=new ConsumerFailureStore(jdbc,template);String event=UUID.randomUUID().toString();
  var properties=new MessageProperties();properties.setMessageId(event);properties.setConsumerQueue(queue);properties.setContentType("application/json");
  byte[] body="{\"value\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  try {
   store.save(new Message(body,properties),new IllegalStateException("consumer failed"));
   var row=jdbc.queryForMap("SELECT failure_id,version FROM reliability_consumer_failure WHERE event_id=?",event);
   String id=row.get("failure_id").toString();long version=((Number)row.get("version")).longValue();
   store.replay(id,version);Message replay=template.receive(queue,5000);
   assertNotNull(replay);assertEquals(event,replay.getMessageProperties().getMessageId());assertArrayEquals(body,replay.getBody());
   assertThrows(ConflictException.class,()->store.replay(id,version));
   store.save(new Message(body,properties),new IllegalStateException("failed again"));
   assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM reliability_consumer_failure WHERE failure_id=?",String.class,id));
   admin.deleteQueue(queue);
   long next=jdbc.queryForObject("SELECT version FROM reliability_consumer_failure WHERE failure_id=?",Long.class,id);
   assertThrows(IllegalStateException.class,()->store.replay(id,next));
   assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM reliability_consumer_failure WHERE failure_id=?",String.class,id));
  } finally {jdbc.update("DELETE FROM reliability_consumer_failure WHERE event_id=?",event);admin.deleteQueue(queue);connection.destroy();}
 }
 private long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
 private int value(){return jdbc.queryForObject("SELECT value FROM test_counter WHERE id=1",Integer.class);}
 private String status(String id){return jdbc.queryForObject("SELECT status FROM reliability_outbox WHERE event_id=?",String.class,id);}
}
