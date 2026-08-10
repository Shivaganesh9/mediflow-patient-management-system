package com.mediflow.medicalrecord.service;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.medicalrecord.dto.CreateMedicalRecordRequest;
import com.mediflow.medicalrecord.entity.MedicalRecord;
import com.mediflow.medicalrecord.repository.MedicalRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalRecordServiceTest {

    @Mock
    private MedicalRecordRepository medicalRecordRepository;

    @InjectMocks
    private MedicalRecordService medicalRecordService;

    @Test
    void createMedicalRecordPersistsRequestData() {
        UUID patientId = UUID.randomUUID();
        CreateMedicalRecordRequest request = request(patientId);
        when(medicalRecordRepository.save(any(MedicalRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MedicalRecord result = medicalRecordService.createMedicalRecord(request);

        assertEquals(patientId, result.getPatientId());
        assertEquals(request.getEncounterDate(), result.getEncounterDate());
        assertEquals(request.getChiefComplaint(), result.getChiefComplaint());
        verify(medicalRecordRepository).save(any(MedicalRecord.class));
    }

    @Test
    void getMedicalRecordThrowsWhenRecordDoesNotExist() {
        UUID recordId = UUID.randomUUID();
        when(medicalRecordRepository.findByRecordId(recordId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> medicalRecordService.getMedicalRecord(recordId));
    }

    private CreateMedicalRecordRequest request(UUID patientId) {
        CreateMedicalRecordRequest request = new CreateMedicalRecordRequest();
        request.setPatientId(patientId);
        request.setAppointmentId(UUID.randomUUID());
        request.setEncounterDate(LocalDate.of(2026, 8, 10));
        request.setChiefComplaint("Routine follow-up");
        request.setNotes("Patient reports improvement.");
        return request;
    }
}
