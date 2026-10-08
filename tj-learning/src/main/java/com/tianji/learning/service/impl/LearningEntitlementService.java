package com.tianji.learning.service.impl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.trade.OrderBasicDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.tianji.common.exceptions.ForbiddenException;
import com.tianji.common.exceptions.ConflictException;
import com.tianji.common.utils.JdbcTime;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor
public class LearningEntitlementService {
    private final JdbcTemplate jdbc;
    @Transactional public void grant(OrderBasicDTO order,List<CourseSimpleInfoDTO> courses) {
        validate(order);
        if(order.getFinishTime()==null || order.getValidDurations()==null || courses.size()!=new HashSet<>(order.getCourseIds()).size())
            throw new IllegalArgumentException("Incomplete order entitlement event");
        var ordered=new TreeMap<Long,CourseSimpleInfoDTO>();
        for(var course:courses) if(ordered.put(course.getId(),course)!=null) throw new IllegalArgumentException("Duplicate course metadata");
        if(!ordered.keySet().equals(new HashSet<>(order.getCourseIds()))) throw new IllegalArgumentException("Course metadata mismatch");
        for(long course:ordered.keySet()) lock(order.getUserId(),course);
        for(CourseSimpleInfoDTO course:ordered.values()) {
            Long detailId=order.getDetailIds()==null?null:order.getDetailIds().get(course.getId());
            if(detailId==null) throw new IllegalArgumentException("Missing order detail provenance");
            Integer duration=order.getValidDurations().get(course.getId());
            if(duration==null || duration<0) throw new IllegalArgumentException("Invalid purchased validity snapshot");
            LocalDateTime expiry=duration<=0?null:order.getFinishTime().plusMonths(duration);
            // A refund arriving before a grant creates a tombstone; it must not be resurrected.
            jdbc.update("INSERT IGNORE INTO learning_entitlement(order_detail_id,order_id,user_id,course_id,active,expires_at) VALUES(?,?,?,?,1,?)",
                    detailId,order.getOrderId(),order.getUserId(),course.getId(),expiry);
            provenance(order,course.getId(),detailId);
            if(jdbc.queryForObject("SELECT active FROM learning_entitlement WHERE order_detail_id=?",Integer.class,detailId)==1)
                jdbc.update("INSERT IGNORE INTO learning_lesson(id,user_id,course_id,status,learned_sections,expire_time) VALUES(?,?,?,0,0,?)",IdWorker.getId(),order.getUserId(),course.getId(),expiry);
            aggregate(order.getUserId(),course.getId());
        }
    }
    @Transactional public void revoke(OrderBasicDTO order) {
        validate(order);
        var courses=new TreeSet<>(order.getCourseIds());
        for(long course:courses) lock(order.getUserId(),course);
        for(Long course:courses) {
            Long detail=order.getDetailIds().get(course);
            if(detail==null) throw new IllegalArgumentException("Missing refund order detail");
            var existing=jdbc.queryForList("SELECT order_id,user_id,course_id FROM learning_entitlement WHERE order_detail_id=? FOR UPDATE",detail);
            if(!existing.isEmpty()) provenance(order,course,detail);
            jdbc.update("INSERT INTO learning_entitlement(order_detail_id,order_id,user_id,course_id,active) VALUES(?,?,?,?,0) ON DUPLICATE KEY UPDATE active=0",
                    detail,order.getOrderId(),order.getUserId(),course);
            aggregate(order.getUserId(),course);
        }
    }
    private void validate(OrderBasicDTO order) {
        if(order==null || order.getOrderId()==null || order.getUserId()==null || order.getCourseIds()==null || order.getCourseIds().isEmpty() || order.getDetailIds()==null)
            throw new IllegalArgumentException("Missing entitlement provenance");
    }
    private void provenance(OrderBasicDTO order,long course,long detail) {
        var row=jdbc.queryForMap("SELECT order_id,user_id,course_id FROM learning_entitlement WHERE order_detail_id=? FOR UPDATE",detail);
        if(((Number)row.get("order_id")).longValue()!=order.getOrderId() || ((Number)row.get("user_id")).longValue()!=order.getUserId() || ((Number)row.get("course_id")).longValue()!=course)
            throw new ConflictException("订单明细权益来源不一致");
    }
    /** All mutations acquire guard, then entitlement rows, then lesson/record rows. */
    public void lock(long user,long course) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Entitlement guard requires a transaction");
        jdbc.update("INSERT INTO learning_entitlement_guard(user_id,course_id) VALUES(?,?) ON DUPLICATE KEY UPDATE course_id=VALUES(course_id)",user,course);
        jdbc.queryForMap("SELECT last_active_status FROM learning_entitlement_guard WHERE user_id=? AND course_id=? FOR UPDATE",user,course);
    }
    private List<Map<String,Object>> current(long user,long course) {
        // FOR UPDATE is a current read, including after waiting on another transaction under MySQL RR.
        return jdbc.queryForList("SELECT expires_at FROM learning_entitlement WHERE user_id=? AND course_id=? AND active=1 AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP(3)) ORDER BY order_detail_id FOR UPDATE",user,course);
    }
    @Transactional public Long available(long user,long course) {
        lock(user,course);
        if(current(user,course).isEmpty()) return null;
        var rows=jdbc.queryForList("SELECT id FROM learning_lesson WHERE user_id=? AND course_id=? FOR UPDATE",user,course);
        return rows.isEmpty()?null:((Number)rows.getFirst().get("id")).longValue();
    }
    @Transactional public long require(long user,long course) {
        Long lesson=available(user,course);
        if(lesson==null) throw new ForbiddenException("无有效课程权益");
        return lesson;
    }
    @Transactional public void refresh(long user,long course) {lock(user,course);aggregate(user,course);}
    public record Summary(LocalDateTime expiresAt,int lastActiveStatus){}
    /** Display uses the same entitlement facts, including before the expiry job runs. */
    public Map<Long,Summary> summaries(long user,Collection<Long> courses) {
        if(courses.isEmpty())return Map.of();
        var unique=new TreeSet<>(courses);var parameters=new ArrayList<Object>();parameters.add(user);parameters.addAll(unique);
        String placeholders=String.join(",",Collections.nCopies(unique.size(),"?"));
        var rows=jdbc.queryForList("SELECT e.course_id,SUM(e.expires_at IS NULL) permanent,MAX(e.expires_at) expiry,COALESCE(MAX(g.last_active_status),0) saved_status FROM learning_entitlement e LEFT JOIN learning_entitlement_guard g ON g.user_id=e.user_id AND g.course_id=e.course_id WHERE e.user_id=? AND e.active=1 AND (e.expires_at IS NULL OR e.expires_at>NOW(3)) AND e.course_id IN ("+placeholders+") GROUP BY e.course_id",parameters.toArray());
        var result=new HashMap<Long,Summary>();
        for(var row:rows)result.put(((Number)row.get("course_id")).longValue(),new Summary(((Number)row.get("permanent")).intValue()>0?null:JdbcTime.localDateTime(row.get("expiry")),((Number)row.get("saved_status")).intValue()));
        return result;
    }
    private void aggregate(long user,long course) {
        var rights=current(user,course);
        var lessons=jdbc.queryForList("SELECT id,status,learned_sections FROM learning_lesson WHERE user_id=? AND course_id=? FOR UPDATE",user,course);
        if(lessons.isEmpty()) return;
        var lesson=lessons.getFirst();int status=((Number)lesson.get("status")).intValue();
        if(rights.isEmpty()) {
            if(status!=3) jdbc.update("UPDATE learning_entitlement_guard SET last_active_status=? WHERE user_id=? AND course_id=?",status,user,course);
            jdbc.update("UPDATE learning_lesson SET status=3,expire_time=NULL WHERE id=?",lesson.get("id"));return;
        }
        LocalDateTime expiry=null;
        if(rights.stream().noneMatch(row->row.get("expires_at")==null))
            expiry=rights.stream().map(row->JdbcTime.localDateTime(row.get("expires_at"))).max(Comparator.naturalOrder()).orElseThrow();
        if(status==3) status=jdbc.queryForObject("SELECT last_active_status FROM learning_entitlement_guard WHERE user_id=? AND course_id=?",Integer.class,user,course);
        jdbc.update("UPDATE learning_lesson SET status=?,expire_time=? WHERE id=?",status,expiry,lesson.get("id"));
        jdbc.update("UPDATE learning_entitlement_guard SET last_active_status=? WHERE user_id=? AND course_id=?",status,user,course);
    }
}
