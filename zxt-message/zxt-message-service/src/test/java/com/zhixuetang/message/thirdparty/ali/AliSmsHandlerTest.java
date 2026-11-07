package com.zhixuetang.message.thirdparty.ali;

import com.aliyun.sdk.service.dysmsapi20170525.AsyncClient;
import com.aliyun.sdk.service.dysmsapi20170525.models.*;
import com.zhixuetang.message.thirdparty.ISmsHandler;
import com.zhixuetang.message.thirdparty.SmsProviderFailure;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AliSmsHandlerTest {
    private final AsyncClient client = mock(AsyncClient.class);
    private final AliSmsHandler handler = new AliSmsHandler(client);

    @Test
    void sendDoesNotReturnBeforeProviderAcceptanceAndCorrelatesTheRequest() throws Exception {
        var response = new CompletableFuture<SendSmsResponse>();
        when(client.sendSms(any())).thenReturn(response);
        var result =
                CompletableFuture.supplyAsync(
                        () ->
                                handler.send(
                                        "13800000001",
                                        "SMS_FIXTURE",
                                        "Fixture",
                                        Map.of("code", "012345"),
                                        "stable-out-id"));
        verify(client, timeout(1000))
                .sendSms(argThat(request -> "stable-out-id".equals(request.getOutId())));
        assertFalse(result.isDone());
        response.complete(
                SendSmsResponse.create().toBuilder()
                        .body(
                                SendSmsResponseBody.builder()
                                        .code("OK")
                                        .bizId("biz-fixture")
                                        .requestId("request-fixture")
                                        .build())
                        .build());
        assertEquals("biz-fixture", result.get(2, TimeUnit.SECONDS).bizId());
    }

    @Test
    void explicitRejectionIsDifferentFromTransportAmbiguityAndNeverLeaksProviderText() {
        when(client.sendSms(any()))
                .thenReturn(
                        CompletableFuture.completedFuture(
                                SendSmsResponse.create().toBuilder()
                                        .body(
                                                SendSmsResponseBody.builder()
                                                        .code("isv.BUSINESS_LIMIT_CONTROL")
                                                        .message("phone=13800000001 code=012345")
                                                        .build())
                                        .build()));
        var rejection =
                assertThrows(
                        SmsProviderFailure.class,
                        () ->
                                handler.send(
                                        "13800000001", "SMS_FIXTURE", "Fixture", Map.of(), "id"));
        assertFalse(rejection.unknown());
        assertEquals("isv.BUSINESS_LIMIT_CONTROL", rejection.getMessage());
        when(client.sendSms(any()))
                .thenReturn(
                        CompletableFuture.failedFuture(
                                new RuntimeException("phone=13800000001 code=012345")));
        var uncertain =
                assertThrows(
                        SmsProviderFailure.class,
                        () ->
                                handler.send(
                                        "13800000001", "SMS_FIXTURE", "Fixture", Map.of(), "id"));
        assertTrue(uncertain.unknown());
        assertFalse(uncertain.toString().contains("13800000001"));
        assertNull(uncertain.getCause());
    }

    @Test
    void receiptRequiresExactPhoneAndOutIdAndPreservesWaitingState() {
        query(List.of(detail("other", 3), detail("out-id", 1)));
        assertEquals(ISmsHandler.State.PENDING, receipt().state());
        query(List.of(detail("other", 3)));
        assertEquals(ISmsHandler.State.UNKNOWN, receipt().state());
        query(List.of(detail("out-id", 3)));
        assertEquals(ISmsHandler.State.DELIVERED, receipt().state());
        query(List.of(detail("out-id", 2)));
        assertEquals(ISmsHandler.State.FAILED, receipt().state());
    }

    @Test
    void conflictingReceiptsRemainUnknown() {
        query(List.of(detail("out-id", 2), detail("out-id", 3)));
        assertEquals(ISmsHandler.State.UNKNOWN, receipt().state());
    }

    private ISmsHandler.Receipt receipt() {
        return handler.query(
                "13800000001", LocalDate.of(2026, 11, 7), null, "out-id", "SMS_FIXTURE");
    }

    private QuerySendDetailsResponseBody.SmsSendDetailDTO detail(String outId, long state) {
        return QuerySendDetailsResponseBody.SmsSendDetailDTO.builder()
                .outId(outId)
                .phoneNum("13800000001")
                .templateCode("SMS_FIXTURE")
                .sendStatus(state)
                .errCode(state == 3 ? "DELIVERED" : "isv.FIXTURE")
                .build();
    }

    private void query(List<QuerySendDetailsResponseBody.SmsSendDetailDTO> details) {
        when(client.querySendDetails(any()))
                .thenReturn(
                        CompletableFuture.completedFuture(
                                QuerySendDetailsResponse.create().toBuilder()
                                        .body(
                                                QuerySendDetailsResponseBody.builder()
                                                        .code("OK")
                                                        .smsSendDetailDTOs(
                                                                QuerySendDetailsResponseBody
                                                                        .SmsSendDetailDTOs.builder()
                                                                        .smsSendDetailDTO(details)
                                                                        .build())
                                                        .build())
                                        .build()));
    }
}
