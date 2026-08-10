package com.mediflow.doctor.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class DoctorResponse {
    UUID doctorId;
    String firstName;
    String lastName;
    String email;
    String phone;
    String specialty;
    Integer yearsOfExperience;
    String department;
    String qualification;
    BigDecimal consultationFee;
    Instant createdAt;
    Instant updatedAt;
    Long version;
}
