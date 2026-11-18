package com.mediflow.appointment.entity;

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
 * Append-only audit entry for an appointment. Rows are written in the same transaction as the
 * change they describe, so a failed booking or transition never leaves a misleading audit trail.
 * The actor is the authenticated username; no clinical notes are copied here.
 */
@Entity
@Table(name = "appointment_history",
        indexes = @Index(name = "idx_appointment_history_appointment",
                columnList = "appointment_id,occurred_at"))
@Getter
@Setter
@NoArgsConstructor
public class AppointmentHistory extends BaseEntity {

    @Column(name = "history_id", nullable = false, unique = true, updatable = false)
    private UUID historyId = UUID.randomUUID();

    @Column(name = "appointment_id", nullable = false, updatable = false)
    private UUID appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 40)
    private AppointmentHistoryAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false, length = 40)
    private AppointmentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 40)
    private AppointmentStatus toStatus;

    @Column(name = "actor", updatable = false, length = 255)
    private String actor;

    /** Short, non-clinical description (for example the previous slot of a reschedule). */
    @Column(name = "details", updatable = false, length = 1000)
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
