import com.tianji.api.client.exam.ExamClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.autoconfigure.reliability.*;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.service.impl.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Ignored acceptance tool. Instantiates deployed production classes, never fake clients or listeners. */
public class LiveSettlementProbe {
 static final JsonMapper JSON=JsonMapper.builder().findAndAddModules().build();
 static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 static int examCalls,userCalls;
 static final List<Map<String,Object>> READINESS=new ArrayList<>();
 static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
 static Object get(String url)throws Exception {
  var response=HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
   .header("X-Internal-Token",System.getenv("TJ_INTERNAL_TOKEN")).header("X-TJ-Call-Depth","1").GET().build(),HttpResponse.BodyHandlers.ofString());
  require(response.statusCode()==200,"Internal HTTP dependency failed");
  Object value=JSON.readValue(response.body(),Object.class);
  if(value instanceof Map<?,?> map && map.containsKey("code")){require(((Number)map.get("code")).intValue()==200,"Internal dependency envelope rejected");return map.get("data");}
  return value;
 }
 static String snapshot(JdbcTemplate jdbc,String month)throws Exception {
  var rows=jdbc.queryForList("SELECT user_id,rank_no,points,CAST(reached_at AS CHAR) reached_at,display_name FROM learning_season_rank WHERE board_month=? ORDER BY rank_no",month);
  return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(rows)));
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
  try {
   var input=JSON.readValue(System.in.readAllBytes(),Map.class);
   String project=(String)input.get("project");require(project.matches("tianji-recovery-[a-z0-9-]+"),"Only recovery projects allowed");
   require("1000".equals(input.get("offset")),"Recovery offset must be 1000");
   require(System.getenv("TJ_INTERNAL_TOKEN")!=null && System.getenv("TJ_INTERNAL_TOKEN").length()>=32,"Missing private service identity");
   String month=(String)input.get("month");var ym=LearningSeasonService.parseMonth(month);
   require(ym.isAfter(YearMonth.now(ZoneId.of("Asia/Shanghai")).plusMonths(1)),"Synthetic season must be future");
   long student=((Number)input.get("student")).longValue(),teacher=((Number)input.get("teacher")).longValue(),admin=((Number)input.get("admin")).longValue();
   long coupon=((Number)input.get("coupon")).longValue(),rule=((Number)input.get("rule")).longValue();
   require(Set.of(student,teacher,admin).size()==3,"Three distinct authorized fixture identities required");
   var ds=new DriverManagerDataSource("jdbc:mysql://mysql:3306/tj_learning?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&readOnlyPropagatesToServer=true","app_learning",(String)input.get("dbPassword"));
   var jdbc=new JdbcTemplate(ds);require("tj_learning".equals(jdbc.queryForObject("SELECT DATABASE()",String.class)),"Wrong database");
   var manager=new DataSourceTransactionManager(ds);var tx=new TransactionTemplate(manager);
   var clock=Clock.fixed(ym.plusMonths(1).atDay(3).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),ZoneId.of("Asia/Shanghai"));
   ExamClient exams=(ExamClient)Proxy.newProxyInstance(ExamClient.class.getClassLoader(),new Class[]{ExamClient.class},(p,m,a)->{
    require(m.getName().equals("settlementReadiness"),"Unexpected ExamClient method");examCalls++;
    var actual=(Map<?,?>)get("http://app-education:26003/_modules/exam/internal/v1/exams/settlement-readiness?before="+URLEncoder.encode((String)a[0],StandardCharsets.UTF_8));
    READINESS.add(Map.of("ready",actual.get("ready"),"pending",actual.get("pending"),"eventCount",actual.get("eventIds") instanceof List<?> ids?ids.size():-1));
    return actual;
   });
   UserClient users=(UserClient)Proxy.newProxyInstance(UserClient.class.getClassLoader(),new Class[]{UserClient.class},(p,m,a)->{
    require(m.getName().equals("queryUserByIds"),"Unexpected UserClient method");userCalls++;
    var ids=new ArrayList<String>();for(Object id:(Iterable<?>)a[0])ids.add(id.toString());
    var data=(List<?>)get("http://app-identity:26001/_modules/user/users/list?ids="+String.join(",",ids));
    return data.stream().map(value->JSON.convertValue(value,UserDTO.class)).toList();
   });
   var outbox=new OutboxStore(jdbc,JSON);var seasons=new LearningSeasonService(jdbc,clock,exams,outbox,users,manager);
   var points=new PointsRecordServiceImpl(jdbc,new InboxStore(jdbc,tx),clock);UserContext.setUser(student);
   var result=new LinkedHashMap<String,Object>();result.put("month",month);result.put("clock",clock.instant().toString());
   if(!"observe".equals(input.get("action"))) {
   require(LearningSeasonService.parseMonth("202402").lengthOfMonth()==29,"Leap month parse failed");
   require(LearningSeasonService.parseMonth("202512").plusMonths(1).equals(YearMonth.of(2026,1)),"Year transition failed");
   for(var edge:List.of(Map.entry("2025-12-31T15:59:59Z","202512"),Map.entry("2025-12-31T16:00:00Z","202601"))){
    var boundary=new LearningSeasonService(jdbc,Clock.fixed(Instant.parse(edge.getKey()),ZoneId.of("Asia/Shanghai")),exams,outbox,users,manager);
    require(boundary.currentMonth().equals(edge.getValue()),"Shanghai UTC date transition failed");
   }
   boolean invalidMonth=false;try{LearningSeasonService.parseMonth("202413");}catch(com.tianji.common.exceptions.BadRequestException expected){invalidMonth=true;}
   require(invalidMonth,"Invalid month accepted");
   var grace=new LearningSeasonService(jdbc,Clock.fixed(ym.plusMonths(1).atDay(1).atTime(23,59,59).atZone(ZoneId.of("Asia/Shanghai")).toInstant(),ZoneId.of("Asia/Shanghai")),exams,outbox,users,manager);
   boolean graceRejected=false;try{grace.settle(month);}catch(com.tianji.common.exceptions.BadRequestException expected){graceRejected=true;}
   require(graceRejected && examCalls==0,"Grace rejection must precede remote/DB effects");result.put("productionCalendarAssertions",6);
   }
   var time=ym.atDay(10).atTime(10,0);String key="live-settlement:"+input.get("runId")+":";
   if("prepare".equals(input.get("action"))) {
    int occupied=jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM learning_season WHERE board_month=?)+(SELECT COUNT(*) FROM points_projection WHERE board_month=?)+(SELECT COUNT(*) FROM learning_reward_rule WHERE board_month=?)+(SELECT COUNT(*) FROM learning_reward_request WHERE board_month=?)",Integer.class,month,month,month,month);
    require(occupied==0,"Synthetic month is occupied");
    // This is the only synthetic rule seed. No snapshot/request/outbox/receipt is seeded.
    jdbc.update("INSERT INTO learning_reward_rule(id,board_month,rank_from,rank_to,coupon_id,enabled,created_by) VALUES(?,?,1,1,?,1,?)",rule,month,coupon,admin);
    points.award(student,10,PointsRecordType.LEARNING,key+"student1",time);
    points.award(student,10,PointsRecordType.LEARNING,key+"student2",time.plusMinutes(1));
    points.award(teacher,10,PointsRecordType.LEARNING,key+"teacher",time);
    points.award(admin,10,PointsRecordType.LEARNING,key+"admin",time);
    points.award(student,10,PointsRecordType.LEARNING,key+"student1",time);
    require(jdbc.queryForObject("SELECT COUNT(*) FROM points_record WHERE board_month=?",Integer.class,month)==4,"Award replay duplicated ledger");
    result.put("preparedLedgerRows",4);result.put("syntheticRuleOnly",true);
   } else if("settle".equals(input.get("action"))) {
    require(jdbc.queryForObject("SELECT COUNT(*) FROM learning_reward_rule WHERE id=? AND board_month=? AND coupon_id=?",Integer.class,rule,month,coupon)==1,"Prepared rule does not match");
    require(jdbc.queryForObject("SELECT COUNT(*) FROM learning_season_rank WHERE board_month=?",Integer.class,month)==0,"Snapshot must be generated by this settlement");
    seasons.settle(month);String hash=snapshot(jdbc,month);seasons.settle(month);
    require("SETTLED".equals(jdbc.queryForObject("SELECT state FROM learning_season WHERE board_month=?",String.class,month)),"Real settlement blocked");
    var ranks=jdbc.queryForList("SELECT user_id FROM learning_season_rank WHERE board_month=? ORDER BY rank_no",Long.class,month);
    require(ranks.equals(List.of(student,Math.min(teacher,admin),Math.max(teacher,admin))),"Actual rank tie ordering failed");
    require(jdbc.queryForObject("SELECT COUNT(*) FROM learning_season_rank WHERE board_month=? AND display_name IS NOT NULL AND display_name<>'' AND display_name<>'同学'",Integer.class,month)==3,"Real user HTTP names not captured");
    points.award(student,10,PointsRecordType.LEARNING,key+"late",time.plusHours(1));
    require(hash.equals(snapshot(jdbc,month)),"Frozen snapshot changed after late event/replay");
    require(jdbc.queryForObject("SELECT COUNT(*) FROM learning_points_reconciliation WHERE board_month=?",Integer.class,month)==1,"Late event reconciliation missing");
    require(jdbc.queryForObject("SELECT COUNT(*) FROM learning_reward_request WHERE board_month=?",Integer.class,month)==1,"Settlement replay duplicated requests");
    var request=jdbc.queryForMap("SELECT request_id FROM learning_reward_request WHERE board_month=?",month);String id=(String)request.get("request_id");
    require(jdbc.queryForObject("SELECT COUNT(*) FROM reliability_outbox WHERE business_key=?",Integer.class,"season-reward:"+id+":1")==1,"Production settlement did not enqueue exactly one event");
    var page=new com.tianji.common.domain.query.PageQuery();page.setPageSize(1);page.setPageNo(2);
    var board=seasons.board(month,page);require(((Number)board.get("total")).intValue()==3 && ((List<?>)board.get("list")).size()==1,"Production frozen board pagination failed");
    require(((Number)((Map<?,?>)((List<?>)board.get("list")).getFirst()).get("rank")).intValue()==2,"Frozen board page rank incorrect");
    result.put("requestId",id);result.put("snapshotSha256",hash);result.put("ledgerRows",5);result.put("snapshotRows",3);result.put("lateReconciliation",true);result.put("paginationAssertions",2);
   } else if("observe".equals(input.get("action"))) {
    // Continuation after an observation-only failure: the DB enforces a read-only transaction.
    tx.setReadOnly(true);
    tx.executeWithoutResult(status->{
     Boolean driverReadOnly=jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection->connection.isReadOnly() && !connection.getAutoCommit());
     require(Boolean.TRUE.equals(driverReadOnly),"JDBC observer transaction is not read-only");
     require(Integer.valueOf(1).equals(jdbc.queryForObject("SELECT @@session.transaction_read_only",Integer.class)),"Server observer transaction is not read-only");
     String id=(String)input.get("requestId");
     require(jdbc.queryForObject("SELECT COUNT(*) FROM learning_reward_request WHERE request_id=? AND board_month=? AND user_id=? AND coupon_id=? AND rule_id=? AND status='FULFILLED'",Integer.class,id,month,student,coupon,rule)==1,"Original reward request differs");
     require("SETTLED".equals(jdbc.queryForObject("SELECT state FROM learning_season WHERE board_month=?",String.class,month)),"Original season is no longer frozen");
     require(jdbc.queryForObject("SELECT COUNT(*) FROM learning_season_rank WHERE board_month=?",Integer.class,month)==3,"Original rank snapshot size differs");
     require(jdbc.queryForObject("SELECT COUNT(*) FROM points_record WHERE board_month=?",Integer.class,month)==5,"Original earned/late points differ");
     require(jdbc.queryForObject("SELECT COUNT(*) FROM learning_points_reconciliation WHERE board_month=?",Integer.class,month)==1,"Original late reconciliation differs");
     try{require(input.get("snapshotSha256").equals(snapshot(jdbc,month)),"Original frozen snapshot changed across restart");}
     catch(Exception error){throw new IllegalStateException("Read-only snapshot comparison failed",error);}
    });
    result.put("readOnly",true);result.put("jdbcReadOnly",true);result.put("sessionTransactionReadOnly",1);result.put("snapshotUnchanged",true);result.put("businessReplayExecuted",false);
   } else {
    require("replay".equals(input.get("action")),"Unsupported action");String id=(String)input.get("requestId");
    require(jdbc.queryForObject("SELECT COUNT(*) FROM learning_reward_request WHERE request_id=? AND board_month=? AND user_id=? AND coupon_id=? AND rule_id=? AND status='FULFILLED'",Integer.class,id,month,student,coupon,rule)==1,"Replay requires the actual fulfilled settlement request");
    require(input.get("snapshotSha256").equals(snapshot(jdbc,month)),"Snapshot changed before replay");
    tx.executeWithoutResult(status->seasons.publishReward(id,student,month,rule,coupon,2));
    new LearningSeasonService(jdbc,clock,exams,outbox,users,manager).settle(month);
    require(input.get("snapshotSha256").equals(snapshot(jdbc,month)),"Snapshot changed during restart replay");
    result.put("requestId",id);result.put("duplicateBusinessRequestPublished",true);result.put("newServiceInstanceSettlementIdempotent",true);
   }
   result.put("examHttpCalls",examCalls);result.put("examReadinessObservations",READINESS);result.put("userHttpCalls",userCalls);result.put("status","PASSED");System.out.println("LIVE_SETTLEMENT_RESULT="+JSON.writeValueAsString(result));
  }catch(Throwable error){System.err.println("LIVE_SETTLEMENT_FAILED="+error.getClass().getSimpleName());error.printStackTrace(System.err);System.exit(1);}finally{UserContext.removeUser();}
 }
}
