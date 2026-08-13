package com.mediflow.patient.service;

import com.mediflow.common.exception.ConflictException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.patient.dto.CreatePatientRequest;
import com.mediflow.patient.dto.PatientResponse;
import com.mediflow.patient.entity.Patient;
import com.mediflow.patient.mapper.PatientMapper;
import com.mediflow.patient.repository.PatientRepository;
import com.mediflow.patient.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientMapper patientMapper;

    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private PatientService patientService;

    @Test
    void createPatientRejectsDuplicateActiveEmail() {
        CreatePatientRequest request = request("alice@example.com");
        when(patientRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> patientService.createPatient(request));
    }

    @Test
    void createPatientByPatientUsesOwnUidAsPatientId() {
        UUID uid = UUID.randomUUID();
        when(currentUser.hasRole("PATIENT")).thenReturn(true);
        when(currentUser.requireCurrentUserId()).thenReturn(uid);
        when(currentUser.currentUsername()).thenReturn("alice");
        when(patientRepository.existsByEmailAndDeletedAtIsNull(any())).thenReturn(false);
        when(patientRepository.existsByPatientIdAndDeletedAtIsNull(uid)).thenReturn(false);
        when(patientMapper.toEntity(any())).thenAnswer(invocation -> new Patient());
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Patient result = patientService.createPatient(request("alice@example.com"));

        assertEquals(uid, result.getPatientId());
    }

    @Test
    void createPatientByPatientRejectsForcingAnotherUsersProfile() {
        when(currentUser.hasRole("PATIENT")).thenReturn(true);
        when(currentUser.requireCurrentUserId()).thenReturn(UUID.randomUUID());

        CreatePatientRequest request = request("alice@example.com");
        request.setUserId(UUID.randomUUID());

        assertThrows(AccessDeniedException.class, () -> patientService.createPatient(request));
        verify(patientRepository, never()).save(any());
    }

    @Test
    void createPatientByAdminUsesExplicitTargetUser() {
        UUID targetUserId = UUID.randomUUID();
        when(currentUser.hasRole("PATIENT")).thenReturn(false);
        when(currentUser.currentUsername()).thenReturn("admin");
        when(patientRepository.existsByEmailAndDeletedAtIsNull(any())).thenReturn(false);
        when(patientRepository.existsByPatientIdAndDeletedAtIsNull(targetUserId)).thenReturn(false);
        when(patientMapper.toEntity(any())).thenAnswer(invocation -> new Patient());
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatePatientRequest request = request("alice@example.com");
        request.setUserId(targetUserId);

        Patient result = patientService.createPatient(request);

        assertEquals(targetUserId, result.getPatientId());
    }

    @Test
    void createPatientByAdminRequiresExplicitTargetUser() {
        when(currentUser.hasRole("PATIENT")).thenReturn(false);
        when(patientRepository.existsByEmailAndDeletedAtIsNull(any())).thenReturn(false);

        CreatePatientRequest request = request("alice@example.com");

        assertThrows(IllegalArgumentException.class, () -> patientService.createPatient(request));
        verify(patientRepository, never()).save(any());
    }

    @Test
    void createPatientRejectsDuplicatePatientIdentity() {
        UUID targetUserId = UUID.randomUUID();
        when(currentUser.hasRole("PATIENT")).thenReturn(false);
        when(patientRepository.existsByEmailAndDeletedAtIsNull(any())).thenReturn(false);
        when(patientRepository.existsByPatientIdAndDeletedAtIsNull(targetUserId)).thenReturn(true);

        CreatePatientRequest request = request("alice@example.com");
        request.setUserId(targetUserId);

        assertThrows(IllegalArgumentException.class, () -> patientService.createPatient(request));
        verify(patientRepository, never()).save(any());
    }

    @Test
    void createPatientRejectsSoftDeletedDuplicateIdentityWithConflict() {
        UUID uid = UUID.randomUUID();
        when(currentUser.hasRole("PATIENT")).thenReturn(true);
        when(currentUser.requireCurrentUserId()).thenReturn(uid);
        when(patientRepository.existsByEmailAndDeletedAtIsNull(any())).thenReturn(false);
        when(patientRepository.existsByPatientIdAndDeletedAtIsNull(uid)).thenReturn(false);
        when(patientRepository.existsByPatientId(uid)).thenReturn(true);

        assertThrows(ConflictException.class, () -> patientService.createPatient(request("alice@example.com")));
        verify(patientRepository, never()).save(any());
    }

    @Test
    void searchPatientsReturnsMappedPage() {
        Patient patient = new Patient();
        PatientResponse response = PatientResponse.builder().patientId(patient.getPatientId()).build();
        when(patientRepository.searchActivePatients("alice", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(patient)));
        when(patientMapper.toResponse(patient)).thenReturn(response);

        var result = patientService.searchPatients("alice", PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals(response, result.getContent().get(0));
    }

    @Test
    void deletePatientSoftDeletesActivePatient() {
        UUID patientId = UUID.randomUUID();
        Patient patient = new Patient();
        when(patientRepository.findByPatientIdAndDeletedAtIsNull(patientId)).thenReturn(Optional.of(patient));

        patientService.deletePatient(patientId);

        verify(patientRepository).save(patient);
        assertEquals(true, patient.getDeletedAt() != null);
    }

    @Test
    void getPatientRejectsDeletedOrMissingPatient() {
        UUID patientId = UUID.randomUUID();
        when(patientRepository.findByPatientIdAndDeletedAtIsNull(patientId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> patientService.getPatient(patientId));
    }

    @Test
    void getPatientRejectsAnotherPatientsProfile() {
        UUID patientId = UUID.randomUUID();
        when(patientRepository.findByPatientIdAndDeletedAtIsNull(patientId)).thenReturn(Optional.of(new Patient()));
        when(currentUser.hasRole("PATIENT")).thenReturn(true);
        when(currentUser.requireCurrentUserId()).thenReturn(UUID.randomUUID());

        assertThrows(AccessDeniedException.class, () -> patientService.getPatient(patientId));
    }

    @Test
    void getPatientAllowsPatientToReadOwnProfile() {
        UUID uid = UUID.randomUUID();
        Patient patient = new Patient();
        when(patientRepository.findByPatientIdAndDeletedAtIsNull(uid)).thenReturn(Optional.of(patient));
        when(currentUser.hasRole("PATIENT")).thenReturn(true);
        when(currentUser.requireCurrentUserId()).thenReturn(uid);

        assertEquals(patient, patientService.getPatient(uid));
    }

    @Test
    void getPatientAllowsAdminAccess() {
        UUID patientId = UUID.randomUUID();
        Patient patient = new Patient();
        when(patientRepository.findByPatientIdAndDeletedAtIsNull(patientId)).thenReturn(Optional.of(patient));
        when(currentUser.hasRole("PATIENT")).thenReturn(false);

        assertEquals(patient, patientService.getPatient(patientId));
    }

    @Test
    void updatePatientRejectsAnotherPatientsProfile() {
        UUID patientId = UUID.randomUUID();
        when(patientRepository.findByPatientIdAndDeletedAtIsNull(patientId)).thenReturn(Optional.of(new Patient()));
        when(currentUser.hasRole("PATIENT")).thenReturn(true);
        when(currentUser.requireCurrentUserId()).thenReturn(UUID.randomUUID());

        assertThrows(AccessDeniedException.class,
                () -> patientService.updatePatient(patientId, request("alice@example.com")));
    }

    private CreatePatientRequest request(String email) {
        CreatePatientRequest request = new CreatePatientRequest();
        request.setFirstName("Alice");
        request.setLastName("Smith");
        request.setEmail(email);
        request.setPhone("555-0100");
        return request;
    }
}
