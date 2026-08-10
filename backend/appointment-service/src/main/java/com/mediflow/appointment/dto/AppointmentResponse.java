package com.mediflow.appointment.dto;

import com.mediflow.appointment.entity.AppointmentStatus;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class AppointmentResponse {
    UUID appointmentId;
    UUID patientId;
    UUID doctorId;
    LocalDateTime startTime;
    LocalDateTime endTime;
    AppointmentStatus status;
    String notes;
    String reason;
    Instant createdAt;
    Instant updatedAt;
    Long version;
}
