package com.mediflow.patient.service;

import com.mediflow.common.exception.ConflictException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.patient.dto.CreatePatientRequest;
import com.mediflow.patient.dto.PatientResponse;
import com.mediflow.patient.entity.Patient;
import com.mediflow.patient.mapper.PatientMapper;
import com.mediflow.patient.repository.PatientRepository;
import com.mediflow.patient.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;
    private final CurrentUser currentUser;

    @Transactional
    public Patient createPatient(CreatePatientRequest request) {
        if (patientRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
            throw new IllegalArgumentException("A patient with this email already exists");
        }

        UUID patientId = resolvePatientIdentity(request.getUserId());
        if (patientRepository.existsByPatientIdAndDeletedAtIsNull(patientId)) {
            throw new IllegalArgumentException("A patient profile already exists for this user");
        }
        if (patientRepository.existsByPatientId(patientId)) {
            throw new ConflictException("A patient profile already exists for this user (previously deleted profile)");
        }

        Patient patient = patientMapper.toEntity(request);
        patient.setPatientId(patientId);
        patient.setCreatedBy(currentUser.currentUsername());
        patient = patientRepository.save(patient);
        log.info("Created patient with id {}", patient.getPatientId());
        return patient;
    }

    @Transactional(readOnly = true)
    public Page<PatientResponse> searchPatients(String search, Pageable pageable) {
        return patientRepository.searchActivePatients(search, pageable).map(patientMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Patient getPatient(UUID patientId) {
        Patient patient = patientRepository.findByPatientIdAndDeletedAtIsNull(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found with id: " + patientId));
        assertPatientProfileAccess(patientId);
        return patient;
    }

    @Transactional
    public Patient updatePatient(UUID patientId, CreatePatientRequest request) {
        Patient patient = getPatient(patientId);
        if (patientRepository.existsByEmailAndPatientIdNotAndDeletedAtIsNull(request.getEmail(), patientId)) {
            throw new IllegalArgumentException("A patient with this email already exists");
        }
        patientMapper.updateEntity(patient, request);
        Patient updatedPatient = patientRepository.save(patient);
        log.info("Updated patient with id {}", patientId);
        return updatedPatient;
    }

    @Transactional
    public void deletePatient(UUID patientId) {
        Patient patient = getPatient(patientId);
        patient.setDeletedAt(java.time.Instant.now());
        patientRepository.save(patient);
        log.info("Soft-deleted patient with id {}", patientId);
    }

    /**
     * Resolves the Auth user identity to be assigned as the patient business id.
     * A patient may only create their own profile; other roles must explicitly
     * identify the target Auth user rather than using their own uid.
     */
    private UUID resolvePatientIdentity(UUID requestedUserId) {
        if (currentUser.hasRole("PATIENT")) {
            UUID selfId = currentUser.requireCurrentUserId();
            if (requestedUserId != null && !requestedUserId.equals(selfId)) {
                throw new AccessDeniedException("Patients can only create their own patient profile");
            }
            return selfId;
        }
        if (requestedUserId == null) {
            throw new IllegalArgumentException("userId is required to associate the patient profile with an Auth user");
        }
        return requestedUserId;
    }

    /**
     * Enforces profile ownership for patients: a patient may only access their
     * own profile (JWT uid == patientId). Admin, staff and doctor roles retain
     * access to patient profiles as required by the application.
     */
    private void assertPatientProfileAccess(UUID patientId) {
        if (currentUser.hasRole("PATIENT")) {
            UUID selfId = currentUser.requireCurrentUserId();
            if (!patientId.equals(selfId)) {
                throw new AccessDeniedException("Patients can only access their own profile");
            }
        }
    }
}