package com.mediflow.medicalrecord.service;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.medicalrecord.dto.CreateMedicalRecordRequest;
import com.mediflow.medicalrecord.dto.UpdateMedicalRecordRequest;
import com.mediflow.medicalrecord.entity.MedicalRecord;
import com.mediflow.medicalrecord.entity.MedicalRecordType;
import com.mediflow.medicalrecord.event.MedicalRecordEventPublisher;
import com.mediflow.medicalrecord.repository.MedicalRecordRepository;
import com.mediflow.medicalrecord.security.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final MedicalRecordEventPublisher eventPublisher;
    private final AccessControlService accessControl;

    @Transactional
    public MedicalRecord createMedicalRecord(CreateMedicalRecordRequest request) {
        MedicalRecord medicalRecord = new MedicalRecord();
        medicalRecord.setPatientId(request.getPatientId());
        medicalRecord.setAppointmentId(request.getAppointmentId());
        medicalRecord.setEncounterDate(request.getEncounterDate());
        medicalRecord.setChiefComplaint(request.getChiefComplaint());
        medicalRecord.setDiagnosis(request.getDiagnosis());
        medicalRecord.setTreatmentPlan(request.getTreatmentPlan());
        medicalRecord.setNotes(request.getNotes());
        medicalRecord.setRecordType(request.getRecordType() == null
                ? MedicalRecordType.CONSULTATION
                : request.getRecordType());
        medicalRecord.setDoctorId(resolveDoctorId(request.getDoctorId()));
        medicalRecord.setCreatedBy(accessControl.currentUsername());

        medicalRecord = medicalRecordRepository.save(medicalRecord);
        eventPublisher.publishMedicalRecordCreated(medicalRecord);
        return medicalRecord;
    }

    @Transactional(readOnly = true)
    public MedicalRecord getMedicalRecord(UUID recordId) {
        MedicalRecord medicalRecord = medicalRecordRepository.findByRecordId(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Medical record not found with id: " + recordId));
        accessControl.assertCanRead(medicalRecord);
        return medicalRecord;
    }

    @Transactional(readOnly = true)
    public Page<MedicalRecord> searchMedicalRecords(UUID patientId, UUID doctorId,
                                                    MedicalRecordType recordType,
                                                    LocalDate fromDate, LocalDate toDate,
                                                    Pageable pageable) {
        UUID scopedPatientId = accessControl.scopedPatientId(patientId);
        UUID scopedDoctorId = accessControl.scopedDoctorId(doctorId);
        return medicalRecordRepository.search(scopedPatientId, scopedDoctorId, recordType, fromDate, toDate, pageable);
    }

    @Transactional
    public MedicalRecord updateMedicalRecord(UUID recordId, UpdateMedicalRecordRequest request) {
        MedicalRecord medicalRecord = medicalRecordRepository.findByRecordIdForUpdate(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Medical record not found with id: " + recordId));
        accessControl.assertCanWrite(medicalRecord);

        // Moving a record to another patient would let a doctor detach it from the
        // patient whose care it documents (and attach it to another patient). Only
        // administrators may correct a mis-assigned patient.
        if (request.getPatientId() != null
                && !request.getPatientId().equals(medicalRecord.getPatientId())
                && !accessControl.isAdmin()) {
            throw new AccessDeniedException("Only administrators can reassign a medical record to another patient");
        }

        if (request.getPatientId() != null) {
            medicalRecord.setPatientId(request.getPatientId());
        }
        if (request.getAppointmentId() != null) {
            medicalRecord.setAppointmentId(request.getAppointmentId());
        }
        if (request.getEncounterDate() != null) {
            medicalRecord.setEncounterDate(request.getEncounterDate());
        }
        if (request.getChiefComplaint() != null) {
            medicalRecord.setChiefComplaint(request.getChiefComplaint());
        }
        if (request.getDiagnosis() != null) {
            medicalRecord.setDiagnosis(request.getDiagnosis());
        }
        if (request.getTreatmentPlan() != null) {
            medicalRecord.setTreatmentPlan(request.getTreatmentPlan());
        }
        if (request.getNotes() != null) {
            medicalRecord.setNotes(request.getNotes());
        }
        if (request.getRecordType() != null) {
            medicalRecord.setRecordType(request.getRecordType());
        }
        if (request.getDoctorId() != null) {
            medicalRecord.setDoctorId(resolveDoctorId(request.getDoctorId()));
        }
        medicalRecord.setUpdatedBy(accessControl.currentUsername());

        medicalRecord = medicalRecordRepository.save(medicalRecord);
        eventPublisher.publishMedicalRecordUpdated(medicalRecord);
        return medicalRecord;
    }

    private UUID resolveDoctorId(UUID requestedDoctorId) {
        if (accessControl.isAdmin()) {
            return requestedDoctorId;
        }
        UUID userId = accessControl.currentUserId();
        if (requestedDoctorId != null && !requestedDoctorId.equals(userId)) {
            throw new AccessDeniedException("Doctors can only create medical records for themselves");
        }
        return userId;
    }
}
