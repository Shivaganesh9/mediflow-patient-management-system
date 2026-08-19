package com.mediflow.patient.seed;

import com.mediflow.common.seed.DevSeedData;
import com.mediflow.patient.entity.Patient;
import com.mediflow.patient.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientDevDataSeederTest {

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private PatientDevDataSeeder seeder;

    @Test
    void seedCreatesTwoPatients() {
        when(patientRepository.existsByPatientId(any())).thenReturn(false);
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

        seeder.seed();

        ArgumentCaptor<Patient> captor = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository, times(2)).save(captor.capture());

        Set<java.util.UUID> ids = captor.getAllValues().stream()
                .map(Patient::getPatientId)
                .collect(Collectors.toSet());
        assertTrue(ids.contains(DevSeedData.PATIENT_USER_ID));
        assertTrue(ids.contains(DevSeedData.SECOND_PATIENT_ID));

        Patient first = captor.getAllValues().stream()
                .filter(p -> p.getPatientId().equals(DevSeedData.PATIENT_USER_ID))
                .findFirst()
                .orElseThrow();
        assertEquals(DevSeedData.PATIENT_EMAIL, first.getEmail());
    }

    @Test
    void seedIsIdempotentWhenPatientsExist() {
        when(patientRepository.existsByPatientId(any())).thenReturn(true);

        seeder.seed();
        seeder.seed();

        verify(patientRepository, never()).save(any(Patient.class));
    }
}