package com.tianji.common.autoconfigure.reliability;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** The unique receipt and side effects commit or roll back together. */
public class InboxStore {
    private final JdbcTemplate jdbc; private final TransactionTemplate tx;
    public InboxStore(JdbcTemplate jdbc,TransactionTemplate tx) { this.jdbc=jdbc; this.tx=tx; }
    public boolean once(String consumer,String eventId,Runnable action) {
        if(eventId==null || eventId.isBlank()) throw new IllegalArgumentException("Missing stable event ID");
        return Boolean.TRUE.equals(tx.execute(status -> {
            int inserted=jdbc.update("INSERT IGNORE INTO reliability_inbox(consumer_name,event_id) VALUES(?,?)",consumer,eventId);
            if(inserted==0) return false;
            action.run(); return true;
        }));
    }
}
