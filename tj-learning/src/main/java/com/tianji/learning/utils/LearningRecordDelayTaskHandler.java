package com.tianji.learning.utils;
import com.tianji.learning.domain.po.LearningRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@Component
@RequiredArgsConstructor
public class LearningRecordDelayTaskHandler {
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager manager;
    private final com.tianji.learning.service.impl.LearningEntitlementService entitlements;
    public void addLearningRecordTask(LearningRecord record) {
        jdbc.update("INSERT INTO learning_progress_pending(record_id,lesson_id,section_id,moment,version,processed_version,updated_at) VALUES(?,?,?,?,1,0,CURRENT_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE moment=GREATEST(moment,VALUES(moment)),version=version+1,updated_at=CURRENT_TIMESTAMP(3)",
                record.getId(),record.getLessonId(),record.getSectionId(),record.getMoment());
    }
    @Scheduled(fixedDelayString="${tj.learning.progress-interval-ms:1000}")
    public void flush() {
        var candidates=jdbc.queryForList("SELECT p.record_id,l.user_id,l.course_id FROM learning_progress_pending p JOIN learning_lesson l ON l.id=p.lesson_id WHERE p.version>p.processed_version ORDER BY l.user_id,l.course_id,p.record_id LIMIT 100");
        for(var candidate:candidates) new TransactionTemplate(manager).executeWithoutResult(tx -> {
                // Persist a previously accepted progress update even after refund, with
                // the same guard order as the producer. History is never discarded.
                entitlements.lock(((Number)candidate.get("user_id")).longValue(),((Number)candidate.get("course_id")).longValue());
                var rows=jdbc.queryForList("SELECT * FROM learning_progress_pending WHERE record_id=? AND version>processed_version FOR UPDATE SKIP LOCKED",candidate.get("record_id"));
                if(rows.isEmpty()) return;
                var row=rows.getFirst();
                jdbc.update("UPDATE learning_record SET moment=GREATEST(COALESCE(moment,0),?) WHERE id=?",row.get("moment"),row.get("record_id"));
                jdbc.update("UPDATE learning_lesson SET latest_section_id=?,latest_learn_time=? WHERE id=? AND (latest_learn_time IS NULL OR latest_learn_time<=?)",
                        row.get("section_id"),row.get("updated_at"),row.get("lesson_id"),row.get("updated_at"));
                jdbc.update("UPDATE learning_progress_pending SET processed_version=? WHERE record_id=? AND version=?",row.get("version"),row.get("record_id"),row.get("version"));
        });
    }
    // Database snapshots replace the former Redis authority and process-local DelayQueue.
    public LearningRecord readRecordCache(Long lesson,Long section) {return null;}
    public void writeRecordCache(LearningRecord record) {}
    public void cleanRecordCache(Long lesson,Long section) {}
}
