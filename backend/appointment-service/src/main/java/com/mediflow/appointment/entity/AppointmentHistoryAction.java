package com.mediflow.appointment.entity;

/**
 * Append-only audit actions recorded for an appointment. The value names the event that happened,
 * while {@link AppointmentHistory#getFromStatus()} and {@link AppointmentHistory#getToStatus()}
 * capture the status change it caused.
 */
public enum AppointmentHistoryAction {
    CREATED,
    RESCHEDULED,
    CONFIRMED,
    STARTED,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}
