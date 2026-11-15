package com.mediflow.appointment.event.outbox;

import com.mediflow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable, transactional record of an event that still has to be published to Kafka.
 *
 * <p>The row is written in the same database transaction as the appointment change, removing the
 * dual-write window between committing the business state and publishing the event. A relay
 * publishes pending rows and marks them afterwards; at-least-once delivery is safe because the
 * consumers are idempotent on {@code eventId}.</p>
 */
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_events_status_next_attempt",
                columnList = "status, next_attempt_at, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent extends BaseEntity {

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(name = "aggregate_id")
    private UUID aggregateId;

    @Column(nullable = false, length = 120)
    private String topic;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "event_class", nullable = false, length = 255)
    private String eventClass;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;
}
