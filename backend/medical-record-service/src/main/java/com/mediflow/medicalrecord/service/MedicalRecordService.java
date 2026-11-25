package com.mediflow.medicalrecord.service;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.common.time.ClinicClock;
import com.mediflow.medicalrecord.dto.CreateMedicalRecordRequest;
import com.mediflow.medicalrecord.dto.UpdateMedicalRecordRequest;
import com.mediflow.medicalrecord.entity.MedicalRecord;
import com.mediflow.medicalrecord.entity.MedicalRecordHistory;
import com.mediflow.medicalrecord.entity.MedicalRecordHistoryAction;
import com.mediflow.medicalrecord.entity.MedicalRecordType;
import com.mediflow.medicalrecord.event.MedicalRecordEventPublisher;
import com.mediflow.medicalrecord.repository.MedicalRecordHistoryRepository;
import com.mediflow.medicalrecord.repository.MedicalRecordRepository;
import com.mediflow.medicalrecord.security.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Medical record creation, reading, updating and audit history.
 *
 * <p>Permissions are unchanged: patients read their own records, doctors read and write only the
 * records they authored, administrators manage all records. An append-only history row is written in
 * the same transaction as each change; it records the actor and the changed field names only, never
 * the clinical values, so no second copy of sensitive data is created.</p>
 *
 * <p>Encounter dates are validated against the clinic clock: a record cannot document an encounter
 * that has not happened yet.</p>
 */
@Service
@RequiredArgsConstructor
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final MedicalRecordHistoryRepository historyRepository;
    private final MedicalRecordEventPublisher eventPublisher;
    private final AccessControlService accessControl;
    private final ClinicClock clinicClock;

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public MedicalRecord createMedicalRecord(CreateMedicalRecordRequest request) {
        validateEncounterDate(request.getEncounterDate());

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
        recordHistory(medicalRecord, MedicalRecordHistoryAction.CREATED, null);
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
    public List<MedicalRecordHistory> getMedicalRecordHistory(UUID recordId) {
        // Reuses the record read rules, so history is exposed exactly to the users who may read the
        // record itself (and never to unrelated doctors, patients or non-clinical staff).
        getMedicalRecord(recordId);
        return historyRepository.findByRecordIdOrderByOccurredAtAscIdAsc(recordId);
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
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
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

        validateEncounterDate(request.getEncounterDate());
        List<String> changedFields = changedFields(medicalRecord, request);

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
        if (!changedFields.isEmpty()) {
            recordHistory(medicalRecord, MedicalRecordHistoryAction.UPDATED,
                    String.join(",", changedFields));
        }
        eventPublisher.publishMedicalRecordUpdated(medicalRecord);
        return medicalRecord;
    }

    private List<String> changedFields(MedicalRecord record, UpdateMedicalRecordRequest request) {
        List<String> changed = new ArrayList<>();
        if (request.getPatientId() != null && !request.getPatientId().equals(record.getPatientId())) {
            changed.add("patientId");
        }
        if (request.getAppointmentId() != null
                && !request.getAppointmentId().equals(record.getAppointmentId())) {
            changed.add("appointmentId");
        }
        if (request.getEncounterDate() != null
                && !request.getEncounterDate().equals(record.getEncounterDate())) {
            changed.add("encounterDate");
        }
        if (request.getChiefComplaint() != null
                && !request.getChiefComplaint().equals(record.getChiefComplaint())) {
            changed.add("chiefComplaint");
        }
        if (request.getDiagnosis() != null && !request.getDiagnosis().equals(record.getDiagnosis())) {
            changed.add("diagnosis");
        }
        if (request.getTreatmentPlan() != null
                && !request.getTreatmentPlan().equals(record.getTreatmentPlan())) {
            changed.add("treatmentPlan");
        }
        if (request.getNotes() != null && !request.getNotes().equals(record.getNotes())) {
            changed.add("notes");
        }
        if (request.getRecordType() != null && !request.getRecordType().equals(record.getRecordType())) {
            changed.add("recordType");
        }
        if (request.getDoctorId() != null && !request.getDoctorId().equals(record.getDoctorId())) {
            changed.add("doctorId");
        }
        return changed;
    }

    private void recordHistory(MedicalRecord medicalRecord, MedicalRecordHistoryAction action,
                               String changedFields) {
        MedicalRecordHistory history = new MedicalRecordHistory();
        history.setRecordId(medicalRecord.getRecordId());
        history.setAction(action);
        history.setActor(accessControl.currentUsername());
        history.setChangedFields(changedFields);
        history.setOccurredAt(clinicClock.instant());
        history.setCreatedBy(accessControl.currentUsername());
        historyRepository.save(history);
    }

    private void validateEncounterDate(LocalDate encounterDate) {
        if (encounterDate != null && encounterDate.isAfter(clinicClock.today())) {
            throw new IllegalArgumentException("Encounter date cannot be in the future");
        }
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
