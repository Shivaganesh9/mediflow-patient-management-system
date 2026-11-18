package com.mediflow.medicalrecord.mapper;

import com.mediflow.medicalrecord.dto.MedicalRecordHistoryResponse;
import com.mediflow.medicalrecord.dto.MedicalRecordResponse;
import com.mediflow.medicalrecord.entity.MedicalRecord;
import com.mediflow.medicalrecord.entity.MedicalRecordHistory;
import org.springframework.stereotype.Component;

@Component
public class MedicalRecordMapper {

    public MedicalRecordResponse toResponse(MedicalRecord medicalRecord) {
        return MedicalRecordResponse.builder()
                .id(medicalRecord.getId())
                .recordId(medicalRecord.getRecordId())
                .patientId(medicalRecord.getPatientId())
                .appointmentId(medicalRecord.getAppointmentId())
                .doctorId(medicalRecord.getDoctorId())
                .encounterDate(medicalRecord.getEncounterDate())
                .recordType(medicalRecord.getRecordType())
                .chiefComplaint(medicalRecord.getChiefComplaint())
                .diagnosis(medicalRecord.getDiagnosis())
                .treatmentPlan(medicalRecord.getTreatmentPlan())
                .notes(medicalRecord.getNotes())
                .version(medicalRecord.getVersion())
                .createdAt(medicalRecord.getCreatedAt())
                .updatedAt(medicalRecord.getUpdatedAt())
                .createdBy(medicalRecord.getCreatedBy())
                .updatedBy(medicalRecord.getUpdatedBy())
                .build();
    }

    public MedicalRecordHistoryResponse toHistoryResponse(MedicalRecordHistory history) {
        return new MedicalRecordHistoryResponse(
                history.getHistoryId(),
                history.getRecordId(),
                history.getAction(),
                history.getActor(),
                history.getChangedFields(),
                history.getOccurredAt());
    }
}
