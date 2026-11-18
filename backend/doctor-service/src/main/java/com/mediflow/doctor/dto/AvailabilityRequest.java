package com.mediflow.doctor.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Data
public class AvailabilityRequest {
    @NotNull private DayOfWeek dayOfWeek;
    @NotNull private LocalTime startTime;
    @NotNull private LocalTime endTime;
    /** Optional on update; when omitted the current active flag is kept. New windows default to active. */
    private Boolean active;
}
