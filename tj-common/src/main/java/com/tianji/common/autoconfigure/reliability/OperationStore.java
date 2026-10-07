package com.tianji.common.autoconfigure.reliability;

import com.tianji.common.exceptions.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public class OperationStore {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final TransactionTemplate tx;
    public OperationStore(JdbcTemplate jdbc, JsonMapper json, TransactionTemplate tx) {
        this.jdbc=jdbc; this.json=json; this.tx=tx;
    }
    public record View(String operationId,String status,Object result,String errorCode,String errorMessage) {}
    public record Work(String id,long userId,String kind,String payload,int attempts) {}
    public View submit(long userId,String kind,String key,Object request) {
        return submit(userId,kind,key,request,request);
    }
    /** The first durable snapshot is retained; only the caller's request defines retry identity. */
    public View submit(long userId,String kind,String key,Object request,Object snapshot) {
        if(key==null || !key.matches("[A-Za-z0-9_.:-]{1,128}"))
            throw new BadRequestException("需要有效的 Idempotency-Key");
        String payload=json.writeValueAsString(snapshot),hash=hash(json.writeValueAsString(canonical(json.readValue(json.writeValueAsString(request),Object.class)))),id=UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO reliability_operation(operation_id,user_id,kind,idempotency_key,request_hash,payload,status,next_attempt_at) VALUES(?,?,?,?,?,?,'PENDING',CURRENT_TIMESTAMP(3))",
                    id,userId,kind,key,hash,payload);
        } catch(DuplicateKeyException duplicate) {
            Map<String,Object> row=jdbc.queryForMap("SELECT operation_id,request_hash FROM reliability_operation WHERE user_id=? AND kind=? AND idempotency_key=?",userId,kind,key);
            if(!hash.equals(row.get("request_hash"))) throw new ConflictException("同一幂等键已用于不同请求");
            id=row.get("operation_id").toString();
        }
        var servletRequest=com.tianji.common.utils.WebUtils.getRequest();
        if(servletRequest!=null){Object audit=servletRequest.getAttribute(AdminAudit.class.getName());if(audit!=null)jdbc.update("UPDATE admin_audit a JOIN reliability_operation o ON o.operation_id=? SET a.operation_id=o.operation_id,a.result=IF(o.status IN('SUCCEEDED','FAILED'),o.status,a.result),a.finished_at=IF(o.status IN('SUCCEEDED','FAILED'),NOW(3),a.finished_at) WHERE a.id=?",id,audit);}
        return get(id,userId);
    }
    public View get(String id,long userId) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT operation_id,status,result,error_code,error_message FROM reliability_operation WHERE operation_id=? AND user_id=?",id,userId);
        if(rows.isEmpty()) throw new BadRequestException("操作不存在");
        Map<String,Object> row=rows.getFirst();
        Object result=row.get("result");
        return new View(id,row.get("status").toString(),result==null?null:json.readValue(result.toString(),Object.class),
                (String)row.get("error_code"),(String)row.get("error_message"));
    }
    public List<Work> due(int limit) {
        return jdbc.query("SELECT * FROM reliability_operation WHERE status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP(3) ORDER BY next_attempt_at LIMIT ?",
                (rs,n)->new Work(rs.getString("operation_id"),rs.getLong("user_id"),rs.getString("kind"),rs.getString("payload"),rs.getInt("attempts")),limit);
    }
    public boolean claim(String id,String token) {
        return jdbc.update("UPDATE reliability_operation SET lease_token=?,attempts=attempts+1,next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 30 SECOND) WHERE operation_id=? AND status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP(3)",token,id)==1;
    }
    public void releaseUnstarted(String id,String token){jdbc.update("UPDATE reliability_operation SET lease_token=NULL,attempts=GREATEST(0,attempts-1),next_attempt_at=CURRENT_TIMESTAMP(3) WHERE operation_id=? AND status='PENDING' AND lease_token=?",id,token);}
    public void execute(Work work,String token,OperationHandler handler) {
        try {
            tx.executeWithoutResult(s -> {
                List<String> locked=jdbc.queryForList("SELECT operation_id FROM reliability_operation WHERE operation_id=? AND status='PENDING' AND lease_token=? FOR UPDATE",String.class,work.id(),token);
                if(locked.isEmpty()) return;
                Object result=handler.execute(work.id(),work.userId(),work.payload());
                jdbc.update("UPDATE reliability_operation SET status='SUCCEEDED',result=?,lease_token=NULL,error_code=NULL,error_message=NULL WHERE operation_id=? AND lease_token=?",
                        json.writeValueAsString(result),work.id(),token);
                jdbc.update("UPDATE admin_audit SET result='SUCCEEDED',finished_at=NOW(3) WHERE operation_id=?",work.id());
            });
        } catch(Exception e) {fail(work,token,e);}
    }

    public List<Work> due(String kind,int limit) {
        return jdbc.query("SELECT * FROM reliability_operation WHERE kind=? AND status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP(3) ORDER BY next_attempt_at LIMIT ?",
                (rs,n)->new Work(rs.getString("operation_id"),rs.getLong("user_id"),rs.getString("kind"),rs.getString("payload"),rs.getInt("attempts")),kind,limit);
    }
    /** Only for idempotent sagas which commit their own local steps; network IO is outside this store's transaction. */
    public void executeWorkflow(Work work,String token,java.util.concurrent.Callable<Object> handler) {
        try {
            Object result=handler.call();
            jdbc.update("UPDATE reliability_operation SET status='SUCCEEDED',result=?,lease_token=NULL,error_code=NULL,error_message=NULL WHERE operation_id=? AND status='PENDING' AND lease_token=?",
                    json.writeValueAsString(result),work.id(),token);
            jdbc.update("UPDATE admin_audit SET result='SUCCEEDED',finished_at=NOW(3) WHERE operation_id=? AND EXISTS(SELECT 1 FROM reliability_operation WHERE operation_id=? AND status='SUCCEEDED')",work.id(),work.id());
        } catch(Exception e) {fail(work,token,e);}
    }
    private void fail(Work work,String token,Exception error){
        boolean business=error instanceof BizIllegalException || error instanceof CommonException known && known.getStatus()>=400 && known.getStatus()<500;
        boolean terminal=business || work.attempts()+1>=10;
        String message=business?Objects.toString(error.getMessage(),"业务请求失败"):"服务处理暂不可用，请稍后重试";
        if(!business)org.slf4j.LoggerFactory.getLogger(OperationStore.class).error("Operation {} ({}) failed",work.id(),work.kind(),error);
        String detail=error.getClass().getSimpleName()+": "+Objects.toString(error.getMessage(),"");
        tx.executeWithoutResult(state->{
            if(jdbc.update("UPDATE reliability_operation SET status=?,lease_token=NULL,error_code=?,error_message=?,next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL ? SECOND) WHERE operation_id=? AND status='PENDING' AND lease_token=?",
                terminal?"FAILED":"PENDING",business?"BUSINESS_FAILED":terminal?"RETRY_EXHAUSTED":"RETRYING",message.substring(0,Math.min(1000,message.length())),Math.min(300,1<<Math.min(work.attempts()+1,8)),work.id(),token)==1)
                {if(terminal)jdbc.update("UPDATE admin_audit SET result='FAILED',finished_at=NOW(3) WHERE operation_id=?",work.id());
                jdbc.update("INSERT INTO reliability_operation_failure(operation_id,last_error) VALUES(?,?) ON DUPLICATE KEY UPDATE last_error=VALUES(last_error),version=version+1",work.id(),detail.substring(0,Math.min(1000,detail.length())));}
        });
    }
    public List<Map<String,Object>> failures(){return jdbc.queryForList("SELECT o.operation_id,o.user_id,o.kind,o.status,o.error_code,o.error_message,o.attempts,f.last_error,f.version FROM reliability_operation o JOIN reliability_operation_failure f ON f.operation_id=o.operation_id WHERE o.status='FAILED' ORDER BY f.updated_at DESC LIMIT 100");}
    public void replayFailure(String id,long version){
        tx.executeWithoutResult(state->{
            var rows=jdbc.queryForList("SELECT o.operation_id FROM reliability_operation o JOIN reliability_operation_failure f ON f.operation_id=o.operation_id WHERE o.operation_id=? AND o.status='FAILED' AND o.error_code='RETRY_EXHAUSTED' AND f.version=? FOR UPDATE",id,version);
            if(rows.isEmpty())throw new ConflictException("操作版本已变化或属于不可重试的业务失败");
            jdbc.update("UPDATE reliability_operation SET status='PENDING',attempts=0,lease_token=NULL,error_code=NULL,error_message=NULL,next_attempt_at=CURRENT_TIMESTAMP(3) WHERE operation_id=?",id);
            jdbc.update("UPDATE reliability_operation_failure SET version=version+1 WHERE operation_id=?",id);
        });
    }
    private static Object canonical(Object value) {
        if(value instanceof Map<?,?> map){var sorted=new TreeMap<String,Object>();map.forEach((key,item)->sorted.put(key.toString(),canonical(item)));return sorted;}
        if(value instanceof List<?> list)return list.stream().map(OperationStore::canonical).toList();
        return value;
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
