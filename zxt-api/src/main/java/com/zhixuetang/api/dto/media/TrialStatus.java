package com.zhixuetang.api.dto.media;

public record TrialStatus(String taskId, State state, String errorCode, Long version) {
    public enum State {
        UNAVAILABLE, MISSING, PENDING, PROCESSING, READY, FAILED, REMOVED
    }

    public static TrialStatus unavailable() {
        return new TrialStatus(null, State.UNAVAILABLE, null, null);
    }
}
