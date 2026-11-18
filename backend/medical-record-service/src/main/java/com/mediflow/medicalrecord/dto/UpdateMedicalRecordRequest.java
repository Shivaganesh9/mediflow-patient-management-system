package com.mediflow.medicalrecord.dto;

import com.mediflow.medicalrecord.entity.MedicalRecordType;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpdateMedicalRecordRequest {

    private UUID patientId;

    private UUID appointmentId;

    private UUID doctorId;

    @PastOrPresent
    private LocalDate encounterDate;

    @Size(max = 500)
    private String chiefComplaint;

    @Size(max = 1000)
    private String diagnosis;

    @Size(max = 2000)
    private String treatmentPlan;

    @Size(max = 10000)
    private String notes;

    private MedicalRecordType recordType;
}
