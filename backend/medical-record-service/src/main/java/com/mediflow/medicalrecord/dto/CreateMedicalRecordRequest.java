package com.mediflow.medicalrecord.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateMedicalRecordRequest {

    @NotNull
    private UUID patientId;

    private UUID appointmentId;

    @NotNull
    private LocalDate encounterDate;

    @NotBlank
    private String chiefComplaint;

    @NotBlank
    private String notes;
}
