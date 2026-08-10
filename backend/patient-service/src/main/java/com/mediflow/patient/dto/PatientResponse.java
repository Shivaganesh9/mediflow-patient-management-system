package com.mediflow.patient.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Value
@Builder
public class PatientResponse {
    UUID patientId;
    String firstName;
    String lastName;
    String email;
    String phone;
    LocalDate dateOfBirth;
    String address;
    String medicalHistory;
    String emergencyContactName;
    String emergencyContactPhone;
    String insuranceProvider;
    String insuranceMemberId;
    Instant createdAt;
    Instant updatedAt;
    Long version;
}
