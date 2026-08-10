package com.mediflow.appointment.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CreateAppointmentRequest {
    @NotNull
    private UUID patientId;

    @NotNull
    private UUID doctorId;

    @NotNull
    @Future
    private LocalDateTime startTime;

    @NotNull
    @Future
    private LocalDateTime endTime;

    private String notes;

    @NotNull
    private String reason;
}
