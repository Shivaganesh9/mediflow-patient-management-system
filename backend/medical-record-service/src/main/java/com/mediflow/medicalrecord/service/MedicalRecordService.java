package com.mediflow.medicalrecord.service;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.medicalrecord.dto.CreateMedicalRecordRequest;
import com.mediflow.medicalrecord.entity.MedicalRecord;
import com.mediflow.medicalrecord.repository.MedicalRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;

    @Transactional
    public MedicalRecord createMedicalRecord(CreateMedicalRecordRequest request) {
        MedicalRecord medicalRecord = new MedicalRecord();
        applyRequest(medicalRecord, request);
        return medicalRecordRepository.save(medicalRecord);
    }

    @Transactional(readOnly = true)
    public MedicalRecord getMedicalRecord(UUID recordId) {
        return medicalRecordRepository.findByRecordId(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Medical record not found with id: " + recordId));
    }

    @Transactional
    public MedicalRecord updateMedicalRecord(UUID recordId, CreateMedicalRecordRequest request) {
        MedicalRecord medicalRecord = getMedicalRecord(recordId);
        applyRequest(medicalRecord, request);
        return medicalRecordRepository.save(medicalRecord);
    }

    private void applyRequest(MedicalRecord medicalRecord, CreateMedicalRecordRequest request) {
        medicalRecord.setPatientId(request.getPatientId());
        medicalRecord.setAppointmentId(request.getAppointmentId());
        medicalRecord.setEncounterDate(request.getEncounterDate());
        medicalRecord.setChiefComplaint(request.getChiefComplaint());
        medicalRecord.setNotes(request.getNotes());
    }
}
