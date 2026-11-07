package com.zhixuetang.message.thirdparty.ali;

import com.aliyun.sdk.service.dysmsapi20170525.AsyncClient;
import com.aliyun.sdk.service.dysmsapi20170525.models.*;
import com.zhixuetang.common.utils.JsonUtils;
import com.zhixuetang.message.thirdparty.ISmsHandler;
import com.zhixuetang.message.thirdparty.SmsProviderFailure;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

@Service("aliYun")
@RequiredArgsConstructor
public class AliSmsHandler implements ISmsHandler {
    private final AsyncClient asyncClient;

    @Override
    public Submission send(
            String phone,
            String template,
            String signName,
            Map<String, String> parameters,
            String outId) {
        var request =
                SendSmsRequest.builder()
                        .phoneNumbers(phone)
                        .templateCode(template)
                        .signName(signName)
                        .templateParam(JsonUtils.toJsonStr(parameters))
                        .outId(outId)
                        .build();
        var response = await(asyncClient.sendSms(request));
        var body = response.getBody();
        if (body == null) throw new SmsProviderFailure("SMS_EMPTY_RESPONSE", true);
        if (!"OK".equals(body.getCode())) throw new SmsProviderFailure(body.getCode(), false);
        if (body.getBizId() == null || body.getBizId().isBlank())
            throw new SmsProviderFailure("SMS_ACCEPTANCE_ID_MISSING", true);
        return new Submission(body.getBizId(), body.getRequestId());
    }

    @Override
    public Receipt query(
            String phone, LocalDate sendDate, String bizId, String outId, String template) {
        // OutId is correlation, not provider idempotency. After a lost response we query by
        // phone/date and require the exact OutId; absence never authorizes a second send.
        Receipt found = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean exhausted = false;
        for (long page = 1; page <= 20; page++) {
            if (System.nanoTime() >= deadline)
                throw new SmsProviderFailure("SMS_RECEIPT_QUERY_TIMEOUT", true);
            var response =
                    await(
                            asyncClient.querySendDetails(
                                    QuerySendDetailsRequest.builder()
                                            .phoneNumber(phone)
                                            .sendDate(
                                                    sendDate.format(
                                                            DateTimeFormatter.BASIC_ISO_DATE))
                                            .bizId(bizId)
                                            .pageSize(50L)
                                            .currentPage(page)
                                            .build()),
                            deadline);
            var body = response.getBody();
            if (body == null || !"OK".equals(body.getCode()))
                throw new SmsProviderFailure(
                        body == null ? "SMS_EMPTY_RESPONSE" : body.getCode(), true);
            var container = body.getSmsSendDetailDTOs();
            var details = container == null ? null : container.getSmsSendDetailDTO();
            if (details == null || details.isEmpty()) {
                exhausted = true;
                break;
            }
            for (var detail : details) {
                if (!outId.equals(detail.getOutId())
                        || !phone.equals(detail.getPhoneNum())
                        || (detail.getTemplateCode() != null
                                && !template.equals(detail.getTemplateCode()))) continue;
                State state =
                        Objects.equals(detail.getSendStatus(), 3L)
                                ? State.DELIVERED
                                : Objects.equals(detail.getSendStatus(), 2L)
                                        ? State.FAILED
                                        : State.PENDING;
                var receipt = new Receipt(state, SmsProviderFailure.safeCode(detail.getErrCode()));
                if (found != null && found.state() != receipt.state())
                    return new Receipt(State.UNKNOWN, "SMS_CONFLICTING_RECEIPTS");
                found = receipt;
            }
            if (details.size() < 50) {
                exhausted = true;
                break;
            }
        }
        if (!exhausted) return new Receipt(State.UNKNOWN, "SMS_RECEIPT_SCAN_LIMIT");
        return found == null ? new Receipt(State.UNKNOWN, "SMS_RECEIPT_NOT_FOUND") : found;
    }

    private static <T> T await(CompletableFuture<T> future) {
        return await(future, System.nanoTime() + TimeUnit.SECONDS.toNanos(10));
    }

    private static <T> T await(CompletableFuture<T> future, long deadline) {
        try {
            return future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new SmsProviderFailure("SMS_REQUEST_INTERRUPTED", true);
        } catch (ExecutionException | TimeoutException error) {
            future.cancel(true);
            // The SDK or network exception may contain request data. Never persist its text.
            throw new SmsProviderFailure("SMS_REQUEST_RESULT_UNKNOWN", true);
        }
    }
}
