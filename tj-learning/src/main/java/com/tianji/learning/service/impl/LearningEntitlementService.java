package com.tianji.learning.service.impl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.trade.OrderBasicDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor
public class LearningEntitlementService {
    private final JdbcTemplate jdbc;
    public void grant(OrderBasicDTO order,List<CourseSimpleInfoDTO> courses) {
        if(order.getOrderId()==null || order.getFinishTime()==null || courses.size()!=new HashSet<>(order.getCourseIds()).size())
            throw new IllegalArgumentException("Incomplete order entitlement event");
        for(CourseSimpleInfoDTO course:courses) {
            Long detailId=order.getDetailIds()==null?null:order.getDetailIds().get(course.getId());
            if(detailId==null) throw new IllegalArgumentException("Missing order detail provenance");
            LocalDateTime expiry=course.getValidDuration()==null || course.getValidDuration()<=0?null:order.getFinishTime().plusMonths(course.getValidDuration());
            // A refund arriving before a grant creates a tombstone; it must not be resurrected.
            int inserted=jdbc.update("INSERT IGNORE INTO learning_entitlement(order_detail_id,order_id,user_id,course_id,active,expires_at) VALUES(?,?,?,?,1,?)",
                    detailId,order.getOrderId(),order.getUserId(),course.getId(),expiry);
            if(inserted==0) continue;
            jdbc.update("INSERT INTO learning_lesson(id,user_id,course_id,status,learned_sections,expire_time) VALUES(?,?,?,0,0,?) ON DUPLICATE KEY UPDATE status=IF(status=3,IF(learned_sections=0,0,1),status),expire_time=CASE WHEN expire_time IS NULL OR VALUES(expire_time) IS NULL THEN NULL ELSE GREATEST(expire_time,VALUES(expire_time)) END",
                    IdWorker.getId(),order.getUserId(),course.getId(),expiry);
        }
    }
    public void revoke(OrderBasicDTO order) {
        if(order.getDetailIds()==null) throw new IllegalArgumentException("Missing refund detail provenance");
        for(Long course:order.getCourseIds()) {
            Long detail=order.getDetailIds().get(course);
            if(detail==null) throw new IllegalArgumentException("Missing refund order detail");
            jdbc.update("INSERT INTO learning_entitlement(order_detail_id,order_id,user_id,course_id,active) VALUES(?,?,?,?,0) ON DUPLICATE KEY UPDATE active=0",
                    detail,order.getOrderId(),order.getUserId(),course);
            jdbc.update("UPDATE learning_lesson l SET status=3 WHERE user_id=? AND course_id=? AND NOT EXISTS(SELECT 1 FROM learning_entitlement e WHERE e.user_id=l.user_id AND e.course_id=l.course_id AND e.active=1 AND (e.expires_at IS NULL OR e.expires_at>NOW()))",
                    order.getUserId(),course);
        }
    }
}
