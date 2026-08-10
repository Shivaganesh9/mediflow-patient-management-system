package com.mediflow.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainEvent<T> {
    private UUID eventId;
    private String eventType;
    private Instant occurredAt;
    private String source;
    private T payload;
}
