package com.tianji.common.autoconfigure.reliability;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.*;

/** Events are committed in the caller's local transaction. No network IO in this class. */
public class OutboxStore {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    public OutboxStore(JdbcTemplate jdbc, JsonMapper json) { this.jdbc=jdbc; this.json=json; }
    public String enqueue(String key, String exchange, String routingKey, Object payload) {
        return enqueue(key, exchange, routingKey, payload, Duration.ZERO);
    }
    public String enqueue(String key, String exchange, String routingKey, Object payload, Duration delay) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Outbox requires the business transaction");
        if(delay==null || delay.isNegative() || delay.compareTo(Duration.ofDays(7))>0) throw new IllegalArgumentException("Invalid durable event delay");
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO reliability_outbox(event_id,business_key,exchange_name,routing_key,event_type,schema_version,payload,delay_ms,status,next_attempt_at) VALUES(?,?,?,?,?,1,?,?,'PENDING',DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL ? MICROSECOND))",
                id,key,exchange,routingKey,routingKey,json.writeValueAsString(new EventEnvelope<>(id,key,routingKey,1,java.time.Instant.now(),payload)),delay.toMillis(),delay.toMillis()*1000);
        return id;
    }
    public record Pending(String id,String businessKey,String exchange,String routingKey,String type,int version,String payload,long delay,int attempts) {}
    public List<Pending> candidates(int limit) {
        return jdbc.query("SELECT * FROM reliability_outbox WHERE status IN ('PENDING','SENDING') AND next_attempt_at<=CURRENT_TIMESTAMP(3) ORDER BY next_attempt_at LIMIT ?",
                (rs,n)->new Pending(rs.getString("event_id"),rs.getString("business_key"),rs.getString("exchange_name"),
                        rs.getString("routing_key"),rs.getString("event_type"),rs.getInt("schema_version"),
                        rs.getString("payload"),rs.getLong("delay_ms"),rs.getInt("attempts")),limit);
    }
    public boolean claim(String id,String token) {
        return jdbc.update("UPDATE reliability_outbox SET status='SENDING',lease_token=?,attempts=attempts+1,next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 30 SECOND) WHERE event_id=? AND status IN ('PENDING','SENDING') AND next_attempt_at<=CURRENT_TIMESTAMP(3)",token,id)==1;
    }
    public void sent(String id,String token) {
        jdbc.update("UPDATE reliability_outbox SET status='SENT',sent_at=CURRENT_TIMESTAMP(3),lease_token=NULL,last_error=NULL WHERE event_id=? AND status='SENDING' AND lease_token=?",id,token);
    }
    public void failed(Pending p,String token,Throwable error) {
        int attempts=p.attempts()+1;
        long seconds=Math.min(300,1L<<Math.min(attempts,8));
        String reason=error.getClass().getSimpleName()+": "+Objects.toString(error.getMessage(),"");
        jdbc.update("UPDATE reliability_outbox SET status=?,lease_token=NULL,next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL ? SECOND),last_error=? WHERE event_id=? AND lease_token=?",
                attempts>=10?"DEAD":"PENDING",seconds,reason.substring(0,Math.min(1000,reason.length())),p.id(),token);
    }
    public int replay(String id) {
        return jdbc.update("UPDATE reliability_outbox SET status='PENDING',attempts=0,last_error=NULL,next_attempt_at=CURRENT_TIMESTAMP(3) WHERE event_id=? AND status='DEAD'",id);
    }
    public List<Map<String,Object>> failures(int limit) {
        return jdbc.queryForList("SELECT event_id,business_key,event_type,status,attempts,last_error,created_at FROM reliability_outbox WHERE status='DEAD' ORDER BY created_at DESC LIMIT ?",Math.min(100,Math.max(1,limit)));
    }
}
