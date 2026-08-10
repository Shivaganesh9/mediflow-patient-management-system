package com.mediflow.patient.service;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.patient.dto.CreatePatientRequest;
import com.mediflow.patient.dto.PatientResponse;
import com.mediflow.patient.entity.Patient;
import com.mediflow.patient.mapper.PatientMapper;
import com.mediflow.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;

    @Transactional
    public Patient createPatient(CreatePatientRequest request) {
        if (patientRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
            throw new IllegalArgumentException("A patient with this email already exists");
        }
        Patient patient = patientRepository.save(patientMapper.toEntity(request));
        log.info("Created patient with id {}", patient.getPatientId());
        return patient;
    }

    @Transactional(readOnly = true)
    public Page<PatientResponse> searchPatients(String search, Pageable pageable) {
        return patientRepository.searchActivePatients(search, pageable).map(patientMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Patient getPatient(UUID patientId) {
        return patientRepository.findByPatientIdAndDeletedAtIsNull(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found with id: " + patientId));
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
}
