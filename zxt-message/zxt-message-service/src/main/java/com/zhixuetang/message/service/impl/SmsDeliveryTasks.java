package com.zhixuetang.message.service.impl;

import com.zhixuetang.api.dto.sms.SmsInfoDTO;
import com.zhixuetang.common.autoconfigure.reliability.OperationHandler;
import com.zhixuetang.common.exceptions.BadRequestException;
import com.zhixuetang.message.thirdparty.SmsProviderFailure;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class SmsDeliveryTasks implements OperationHandler {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final ObjectProvider<SmsProviderRegistry> providers;
    private final ThreadPoolTaskExecutor executor;
    private final TransactionTemplate tx;
    private final boolean simulated;

    public SmsDeliveryTasks(
            JdbcTemplate jdbc,
            JsonMapper json,
            ObjectProvider<SmsProviderRegistry> providers,
            @Qualifier("asyncSmsExecutor") ThreadPoolTaskExecutor executor,
            PlatformTransactionManager manager,
            Environment environment) {
        this.jdbc = jdbc;
        this.json = json;
        this.providers = providers;
        this.executor = executor;
        tx = new TransactionTemplate(manager);
        simulated = environment.getProperty("zxt.sms.simulated", Boolean.class, false);
        if (simulated
                && Arrays.stream(environment.getActiveProfiles())
                        .noneMatch(Set.of("acceptance", "local-simulator")::contains))
            throw new IllegalStateException("Simulated SMS forbidden in real environment");
    }

    public void stage(String id, SmsInfoDTO message) {
        if (message == null
                || message.getPhones() == null
                || message.getTemplateCode() == null
                || !message.getTemplateCode().matches("[A-Za-z0-9_-]{1,50}"))
            throw new BadRequestException("短信模板或接收人无效");
        var phones =
                java.util.stream.StreamSupport.stream(message.getPhones().spliterator(), false)
                        .limit(101)
                        .toList();
        if (phones.isEmpty()
                || phones.size() > 100
                || phones.stream().anyMatch(p -> p == null || !p.matches("[0-9+]{6,20}")))
            throw new BadRequestException("短信接收人无效");
        if ("VERIFY_CODE".equals(message.getTemplateCode()) && message.getExpiresAt() == null)
            throw new BadRequestException("验证码短信必须提供最晚发送时间");
        Timestamp expires =
                message.getExpiresAt() == null
                        ? null
                        : Timestamp.from(Instant.ofEpochMilli(message.getExpiresAt()));
        tx.executeWithoutResult(
                status -> {
                    int created =
                            jdbc.update(
                                    "INSERT IGNORE INTO sms_delivery_task(id,payload) VALUES(?,?)",
                                    id,
                                    json.writeValueAsString(message));
                    if (created == 0) return;
                    for (String phone : new LinkedHashSet<>(phones)) {
                        String recipient =
                                UUID.nameUUIDFromBytes(
                                                (id + "\n" + phone)
                                                        .getBytes(
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_8))
                                        .toString();
                        jdbc.update(
                                "INSERT INTO sms_delivery_recipient(id,task_id,phone,expires_at) VALUES(?,?,?,?)",
                                recipient,
                                id,
                                phone,
                                expires);
                    }
                });
    }

    public String kind() {
        return "SMS_SEND";
    }

    public Object execute(String operation, long user, String payload) {
        stage(operation, json.readValue(payload, SmsInfoDTO.class));
        return Map.of("deliveryId", operation, "mode", simulated ? "SIMULATED" : "REAL");
    }

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        for (var row :
                jdbc.queryForList(
                        "SELECT id FROM sms_delivery_recipient WHERE state IN('PENDING','SUBMITTING','ACCEPTED','UNKNOWN') AND next_attempt_at<=NOW(3) AND (lease_until IS NULL OR lease_until<NOW(3)) AND (receipt_deadline IS NULL OR receipt_deadline>NOW(3)) ORDER BY next_attempt_at,id LIMIT 20")) {
            try {
                executor.execute(() -> process(row.get("id").toString()));
            } catch (org.springframework.core.task.TaskRejectedException rejected) {
                break;
            }
        }
        // A missing receipt remains unknown, never delivered. Stop polling after one day.
        for (var row :
                jdbc.queryForList(
                        "SELECT DISTINCT task_id FROM sms_delivery_recipient WHERE state IN('ACCEPTED','SUBMITTING','UNKNOWN') AND receipt_deadline<=NOW(3) AND (last_error IS NULL OR last_error<>'SMS_RECEIPT_DEADLINE') AND (lease_until IS NULL OR lease_until<NOW(3)) LIMIT 20")) {
            jdbc.update(
                    "UPDATE sms_delivery_recipient SET state='UNKNOWN',last_error='SMS_RECEIPT_DEADLINE',version=version+1 WHERE task_id=? AND state IN('ACCEPTED','SUBMITTING','UNKNOWN') AND receipt_deadline<=NOW(3) AND (last_error IS NULL OR last_error<>'SMS_RECEIPT_DEADLINE') AND (lease_until IS NULL OR lease_until<NOW(3))",
                    row.get("task_id"));
            summarize(row.get("task_id").toString());
        }
    }

    void process(String id) {
        String token = UUID.randomUUID().toString();
        if (jdbc.update(
                        "UPDATE sms_delivery_recipient SET lease_token=?,lease_until=NOW(3)+INTERVAL 60 SECOND WHERE id=? AND state IN('PENDING','SUBMITTING','ACCEPTED','UNKNOWN') AND next_attempt_at<=NOW(3) AND (lease_until IS NULL OR lease_until<NOW(3)) AND (receipt_deadline IS NULL OR receipt_deadline>NOW(3))",
                        token,
                        id)
                != 1) return;
        var row =
                jdbc.queryForMap(
                        "SELECT r.*,UNIX_TIMESTAMP(r.expires_at)*1000 expires_ms,DATE_FORMAT(r.submitted_at,'%Y%m%d') send_date,t.payload FROM sms_delivery_recipient r JOIN sms_delivery_task t ON t.id=r.task_id WHERE r.id=? AND r.lease_token=?",
                        id, token);
        String task = row.get("task_id").toString();
        try {
            if ("PENDING".equals(row.get("state"))) submit(row, token);
            else reconcile(row, token);
        } finally {
            summarize(task);
        }
    }

    private void submit(Map<String, Object> row, String token) {
        Object id = row.get("id");
        if (row.get("expires_ms") != null
                && ((Number) row.get("expires_ms")).longValue() <= System.currentTimeMillis()) {
            finish(id, token, "EXPIRED", "SMS_EXPIRED");
            return;
        }
        if (simulated) {
            tx.executeWithoutResult(
                    status -> {
                        jdbc.update(
                                "INSERT IGNORE INTO simulated_sms(task_id,payload) VALUES(?,?)",
                                row.get("task_id"),
                                row.get("payload"));
                        finish(id, token, "SIMULATED", null);
                    });
            return;
        }
        var message = json.readValue(row.get("payload").toString(), SmsInfoDTO.class);
        com.zhixuetang.message.domain.po.MessageTemplate route;
        try {
            route = providers.getObject().select(message);
        } catch (RuntimeException failure) {
            finish(
                    id,
                    token,
                    "FAILED",
                    failure instanceof com.zhixuetang.common.exceptions.CommonException
                            ? SmsProviderFailure.safeCode(failure.getMessage())
                            : "SMS_CONFIGURATION_QUERY_FAILED");
            return;
        }
        String outId = UUID.randomUUID().toString();
        // Commit intent before network IO. A process death from here requires reconciliation.
        if (jdbc.update(
                        "UPDATE sms_delivery_recipient SET state='SUBMITTING',provider_code=?,provider_template=?,sign_name=?,out_id=?,submitted_at=NOW(3),receipt_deadline=NOW(3)+INTERVAL 1 DAY,attempts=attempts+1,version=version+1 WHERE id=? AND lease_token=? AND lease_until>NOW(3) AND (expires_at IS NULL OR expires_at>NOW(3))",
                        route.getPlatformCode(),
                        route.getThirdTemplateCode(),
                        route.getSignName(),
                        outId,
                        id,
                        token)
                != 1) return;
        try {
            var result =
                    providers
                            .getObject()
                            .handler(route.getPlatformCode())
                            .send(
                                    row.get("phone").toString(),
                                    route.getThirdTemplateCode(),
                                    route.getSignName(),
                                    message.getTemplateParams(),
                                    outId);
            jdbc.update(
                    "UPDATE sms_delivery_recipient SET state='ACCEPTED',biz_id=?,request_id=?,last_error=NULL,lease_token=NULL,lease_until=NULL,next_attempt_at=NOW(3)+INTERVAL 30 SECOND,version=version+1 WHERE id=? AND lease_token=?",
                    result.bizId(),
                    result.requestId(),
                    id,
                    token);
        } catch (SmsProviderFailure failure) {
            if (failure.unknown()) {
                defer(id, token, "UNKNOWN", failure.getMessage(), 30);
            } else {
                int attempts = ((Number) row.get("attempts")).intValue() + 1;
                defer(
                        id,
                        token,
                        attempts >= 10 ? "FAILED" : "PENDING",
                        failure.getMessage(),
                        Math.min(300, 1 << Math.min(attempts, 8)));
            }
        } catch (RuntimeException failure) {
            defer(id, token, "UNKNOWN", "SMS_REQUEST_RESULT_UNKNOWN", 30);
        }
    }

    private void reconcile(Map<String, Object> row, String token) {
        Object id = row.get("id");
        try {
            var receipt =
                    providers
                            .getObject()
                            .handler(row.get("provider_code").toString())
                            .query(
                                    row.get("phone").toString(),
                                    LocalDate.parse(
                                            row.get("send_date").toString(),
                                            java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                                    (String) row.get("biz_id"),
                                    row.get("out_id").toString(),
                                    row.get("provider_template").toString());
            switch (receipt.state()) {
                case DELIVERED -> finish(id, token, "DELIVERED", null);
                case FAILED -> finish(id, token, "FAILED", receipt.code());
                case PENDING -> defer(id, token, "ACCEPTED", null, 30);
                case UNKNOWN -> defer(id, token, "UNKNOWN", receipt.code(), 60);
            }
        } catch (RuntimeException failure) {
            defer(id, token, "UNKNOWN", "SMS_RECEIPT_QUERY_FAILED", 60);
        }
    }

    private void finish(Object id, String token, String state, String code) {
        jdbc.update(
                "UPDATE sms_delivery_recipient SET state=?,last_error=?,lease_token=NULL,lease_until=NULL,version=version+1 WHERE id=? AND lease_token=?",
                state,
                code,
                id,
                token);
    }

    private void defer(Object id, String token, String state, String code, int seconds) {
        jdbc.update(
                "UPDATE sms_delivery_recipient SET state=?,last_error=?,lease_token=NULL,lease_until=NULL,next_attempt_at=NOW(3)+INTERVAL ? SECOND,version=version+1 WHERE id=? AND lease_token=?",
                state,
                code,
                seconds,
                id,
                token);
    }

    private void summarize(String task) {
        tx.executeWithoutResult(
                status -> {
                    jdbc.queryForObject(
                            "SELECT id FROM sms_delivery_task WHERE id=? FOR UPDATE",
                            String.class,
                            task);
                    var children =
                            jdbc.queryForList(
                                    "SELECT state,attempts,last_error FROM sms_delivery_recipient WHERE task_id=?",
                                    task);
                    Set<String> states = new HashSet<>();
                    int attempts = 0;
                    String error = null;
                    for (var child : children) {
                        states.add(child.get("state").toString());
                        attempts += ((Number) child.get("attempts")).intValue();
                        if (child.get("last_error") != null)
                            error = child.get("last_error").toString();
                    }
                    String state =
                            states.stream().allMatch(Set.of("DELIVERED", "SIMULATED")::contains)
                                    ? "DONE"
                                    : states.contains("PENDING") || states.contains("SUBMITTING")
                                            ? "PENDING"
                                            : states.contains("UNKNOWN")
                                                    ? "UNKNOWN"
                                                    : states.contains("ACCEPTED")
                                                            ? "ACCEPTED"
                                                            : states.contains("FAILED")
                                                                    ? "DEAD"
                                                                    : "EXPIRED";
                    jdbc.update(
                            "UPDATE sms_delivery_task SET status=?,attempts=?,last_error=?,version=version+1 WHERE id=?",
                            state,
                            attempts,
                            error,
                            task);
                });
    }
}
