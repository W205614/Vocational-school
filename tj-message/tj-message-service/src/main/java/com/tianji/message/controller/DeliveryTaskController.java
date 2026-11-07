package com.tianji.message.controller;

import com.tianji.common.utils.UserContext;
import com.tianji.common.exceptions.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/admin/deliveries")
public class DeliveryTaskController {
    private final JdbcTemplate jdbc;

    @GetMapping("/{kind}")
    public Object list(@PathVariable String kind, @RequestParam(required = false) String taskId) {
        UserContext.requireAdmin();
        return switch (kind) {
            case "sms" -> sms(taskId);
            case "notice" ->
                    jdbc.queryForList(
                            "SELECT id,delivery_status status,delivery_attempts attempts,delivery_error last_error,delivery_version version,delivery_next_attempt next_attempt_at FROM notice_task WHERE finished=0 ORDER BY delivery_next_attempt LIMIT 100");
            default -> throw new BadRequestException("通知任务类型无效");
        };
    }

    private Object sms(String taskId) {
        if (taskId != null && !taskId.matches("[0-9a-f-]{36}"))
            throw new BadRequestException("短信任务编号无效");
        var rows =
                taskId == null
                        ? jdbc.queryForList(
                                "SELECT id,status,attempts,CASE WHEN last_error REGEXP '^[A-Za-z0-9_.-]{1,100}$' THEN last_error WHEN last_error IS NOT NULL THEN 'LEGACY_ERROR_REDACTED' END last_error,version,created_at FROM sms_delivery_task ORDER BY created_at DESC,id LIMIT 100")
                        : jdbc.queryForList(
                                "SELECT id,status,attempts,CASE WHEN last_error REGEXP '^[A-Za-z0-9_.-]{1,100}$' THEN last_error WHEN last_error IS NOT NULL THEN 'LEGACY_ERROR_REDACTED' END last_error,version,created_at FROM sms_delivery_task WHERE id=?",
                                taskId);
        for (var row : rows)
            row.put(
                    "recipients",
                    jdbc.queryForList(
                            "SELECT id,CONCAT('***',RIGHT(phone,4)) masked_phone,state,provider_code,biz_id,request_id,last_error,version,submitted_at,expires_at,next_attempt_at FROM sms_delivery_recipient WHERE task_id=? ORDER BY id",
                            row.get("id")));
        return rows;
    }

    @PostMapping("/{kind}/{id}/replay")
    @org.springframework.transaction.annotation.Transactional
    public void replay(
            @PathVariable String kind, @PathVariable String id, @RequestParam long version) {
        UserContext.requireAdmin();
        int changed =
                switch (kind) {
                    case "sms" -> replaySms(id, version);
                    case "notice" ->
                            jdbc.update(
                                    "UPDATE notice_task SET delivery_status='PENDING',delivery_attempts=0,delivery_error=NULL,delivery_next_attempt=NOW(3),delivery_version=delivery_version+1 WHERE id=? AND delivery_status='DEAD' AND delivery_version=?",
                                    id,
                                    version);
                    default -> throw new BadRequestException("通知任务类型无效");
                };
        if (changed != 1) throw new ConflictException("任务版本已变化或任务尚未失败");
    }

    private int replaySms(String id, long version) {
        int changed =
                jdbc.update(
                        "UPDATE sms_delivery_task SET status='PENDING',attempts=0,last_error=NULL,next_attempt_at=NOW(3),version=version+1 WHERE id=? AND status='DEAD' AND version=? AND EXISTS(SELECT 1 FROM sms_delivery_recipient r WHERE r.task_id=sms_delivery_task.id AND r.state='FAILED' AND (r.expires_at IS NULL OR r.expires_at>NOW(3)))",
                        id,
                        version);
        if (changed == 1)
            jdbc.update(
                    "UPDATE sms_delivery_recipient SET state='PENDING',attempts=0,last_error=NULL,biz_id=NULL,request_id=NULL,out_id=NULL,receipt_deadline=NULL,next_attempt_at=NOW(3),version=version+1 WHERE task_id=? AND state='FAILED' AND (expires_at IS NULL OR expires_at>NOW(3))",
                    id);
        return changed;
    }
}
