package com.mediflow.doctor.dto;

/**
 * Result of the internal schedule check used by appointment-service.
 *
 * <p>{@code valid} is false when the requested slot falls outside the doctor's active weekly
 * availability or overlaps an active leave. The reason is safe to return to the caller (it names
 * only the doctor's schedule, never patient data).</p>
 */
public record ScheduleValidationResponse(boolean valid, String reason) {

    public static ScheduleValidationResponse ok() {
        return new ScheduleValidationResponse(true, "Slot is within the doctor's working hours");
    }

    public static ScheduleValidationResponse rejected(String reason) {
        return new ScheduleValidationResponse(false, reason);
    }
}
