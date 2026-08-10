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
}
