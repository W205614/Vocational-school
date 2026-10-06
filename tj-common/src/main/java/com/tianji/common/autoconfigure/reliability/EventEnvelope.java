package com.tianji.common.autoconfigure.reliability;
import java.time.Instant;
public record EventEnvelope<T>(String eventId, String businessKey, String eventType,
                               int schemaVersion, Instant occurredAt, T payload) {}
