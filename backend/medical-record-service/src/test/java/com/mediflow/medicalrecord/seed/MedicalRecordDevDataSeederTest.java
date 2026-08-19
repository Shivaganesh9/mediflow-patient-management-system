package com.mediflow.medicalrecord.seed;

import com.mediflow.common.seed.DevSeedData;
import com.mediflow.medicalrecord.entity.MedicalRecord;
import com.mediflow.medicalrecord.repository.MedicalRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicalRecordDevDataSeederTest {

    @Mock
    private MedicalRecordRepository medicalRecordRepository;

    @InjectMocks
    private MedicalRecordDevDataSeeder seeder;

    @Test
    void seedCreatesTwoRecords() {
        when(medicalRecordRepository.findByRecordId(any())).thenReturn(Optional.empty());
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        seeder.seed();

        ArgumentCaptor<MedicalRecord> captor = ArgumentCaptor.forClass(MedicalRecord.class);
        verify(medicalRecordRepository, times(2)).save(captor.capture());

        Set<java.util.UUID> ids = captor.getAllValues().stream()
                .map(MedicalRecord::getRecordId)
                .collect(Collectors.toSet());
        assertEquals(Set.of(DevSeedData.MEDICAL_RECORD_1_ID, DevSeedData.MEDICAL_RECORD_2_ID), ids);

        MedicalRecord first = captor.getAllValues().stream()
                .filter(r -> r.getRecordId().equals(DevSeedData.MEDICAL_RECORD_1_ID))
                .findFirst()
                .orElseThrow();
        assertEquals(DevSeedData.PATIENT_USER_ID, first.getPatientId());
        assertEquals(DevSeedData.DOCTOR_USER_ID, first.getDoctorId());
    }

    @Test
    void seedIsIdempotentWhenRecordsExist() {
        when(medicalRecordRepository.findByRecordId(any()))
                .thenAnswer(inv -> Optional.of(new MedicalRecord()));

        seeder.seed();
        seeder.seed();

        verify(medicalRecordRepository, never()).save(any(MedicalRecord.class));
    }
}