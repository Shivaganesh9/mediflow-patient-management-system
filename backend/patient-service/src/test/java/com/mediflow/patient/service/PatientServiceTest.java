package com.mediflow.patient.service;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.patient.dto.CreatePatientRequest;
import com.mediflow.patient.dto.PatientResponse;
import com.mediflow.patient.entity.Patient;
import com.mediflow.patient.mapper.PatientMapper;
import com.mediflow.patient.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientMapper patientMapper;

    @InjectMocks
    private PatientService patientService;

    @Test
    void createPatientRejectsDuplicateActiveEmail() {
        CreatePatientRequest request = request("alice@example.com");
        when(patientRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> patientService.createPatient(request));
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

    private CreatePatientRequest request(String email) {
        CreatePatientRequest request = new CreatePatientRequest();
        request.setFirstName("Alice");
        request.setLastName("Smith");
        request.setEmail(email);
        request.setPhone("555-0100");
        return request;
    }
}
