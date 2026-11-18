package com.mediflow.appointment.dto;

import com.mediflow.appointment.entity.AppointmentHistoryAction;
import com.mediflow.appointment.entity.AppointmentStatus;

import java.time.Instant;
import java.util.UUID;

/** One entry of an appointment's audit trail. */
public record AppointmentHistoryResponse(
        UUID historyId,
        UUID appointmentId,
        AppointmentHistoryAction action,
        AppointmentStatus fromStatus,
        AppointmentStatus toStatus,
        String actor,
        String details,
        Instant occurredAt) {
}
