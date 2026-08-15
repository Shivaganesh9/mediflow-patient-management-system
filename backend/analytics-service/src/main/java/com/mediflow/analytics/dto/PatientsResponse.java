package com.mediflow.analytics.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class PatientsResponse {
    LocalDate from;
    LocalDate to;
    long totalPatients;
    long newPatients;
    List<PatientActivityResponse> mostActive;

    @Value
    @Builder
    public static class PatientActivityResponse {
        UUID patientId;
        long appointments;
        LocalDate firstAppointment;
    }
}