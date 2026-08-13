package com.mediflow.medicalrecord.dto;

import com.mediflow.medicalrecord.entity.MedicalRecordType;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Value
@Builder
public class MedicalRecordResponse {
    UUID id;
    UUID recordId;
    UUID patientId;
    UUID appointmentId;
    UUID doctorId;
    LocalDate encounterDate;
    MedicalRecordType recordType;
    String chiefComplaint;
    String diagnosis;
    String treatmentPlan;
    String notes;
    Long version;
    Instant createdAt;
    Instant updatedAt;
    String createdBy;
    String updatedBy;
}
