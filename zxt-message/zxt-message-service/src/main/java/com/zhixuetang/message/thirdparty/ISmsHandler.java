package com.zhixuetang.message.thirdparty;

import java.time.LocalDate;
import java.util.Map;

public interface ISmsHandler {
    /** A response is provider acceptance, never a handset delivery acknowledgement. */
    Submission send(
            String phone,
            String template,
            String signName,
            Map<String, String> parameters,
            String outId);

    Receipt query(String phone, LocalDate sendDate, String bizId, String outId, String template);

    record Submission(String bizId, String requestId) {}

    enum State {
        PENDING,
        DELIVERED,
        FAILED,
        UNKNOWN
    }

    record Receipt(State state, String code) {}
}
