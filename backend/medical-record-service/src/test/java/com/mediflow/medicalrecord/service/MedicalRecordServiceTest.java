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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalRecordServiceTest {

    @Mock
    private MedicalRecordRepository medicalRecordRepository;

    @Mock
    private MedicalRecordHistoryRepository historyRepository;

    @Mock
    private MedicalRecordEventPublisher eventPublisher;

    @Mock
    private AccessControlService accessControl;

    @Mock
    private ClinicClock clinicClock;

    @InjectMocks
    private MedicalRecordService medicalRecordService;

    @BeforeEach
    void setUp() {
        lenient().when(clinicClock.today()).thenReturn(LocalDate.now());
        lenient().when(clinicClock.instant()).thenReturn(Instant.now());
        lenient().when(accessControl.currentUsername()).thenReturn("doctor1");
    }

    @Test
    void createMedicalRecordPersistsRequestData() {
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        CreateMedicalRecordRequest request = request(patientId);

        when(accessControl.isAdmin()).thenReturn(false);
        when(accessControl.currentUserId()).thenReturn(doctorId);
        when(accessControl.currentUsername()).thenReturn("doctor1");
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MedicalRecord result = medicalRecordService.createMedicalRecord(request);

        assertEquals(patientId, result.getPatientId());
        assertEquals(request.getEncounterDate(), result.getEncounterDate());
        assertEquals(request.getChiefComplaint(), result.getChiefComplaint());
        assertEquals(doctorId, result.getDoctorId());
        assertEquals(MedicalRecordType.CONSULTATION, result.getRecordType());
        assertEquals("doctor1", result.getCreatedBy());
        verify(medicalRecordRepository).save(any(MedicalRecord.class));
        verify(eventPublisher).publishMedicalRecordCreated(any(MedicalRecord.class));
    }

    @Test
    void createMedicalRecordAppliesClinicalFields() {
        UUID doctorId = UUID.randomUUID();
        CreateMedicalRecordRequest request = request(UUID.randomUUID());
        request.setDoctorId(doctorId);
        request.setRecordType(MedicalRecordType.FOLLOW_UP);
        request.setDiagnosis("Hypertension stable");
        request.setTreatmentPlan("Continue medication");

        when(accessControl.isAdmin()).thenReturn(false);
        when(accessControl.currentUserId()).thenReturn(doctorId);
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MedicalRecord result = medicalRecordService.createMedicalRecord(request);

        assertEquals(MedicalRecordType.FOLLOW_UP, result.getRecordType());
        assertEquals("Hypertension stable", result.getDiagnosis());
        assertEquals("Continue medication", result.getTreatmentPlan());
        assertEquals(doctorId, result.getDoctorId());
    }

    @Test
    void createMedicalRecordRejectsDoctorIdMismatch() {
        CreateMedicalRecordRequest request = request(UUID.randomUUID());
        request.setDoctorId(UUID.randomUUID());

        when(accessControl.isAdmin()).thenReturn(false);
        when(accessControl.currentUserId()).thenReturn(UUID.randomUUID());

        assertThrows(AccessDeniedException.class, () -> medicalRecordService.createMedicalRecord(request));
        verify(medicalRecordRepository, never()).save(any(MedicalRecord.class));
    }

    @Test
    void createMedicalRecordByAdminUsesProvidedDoctorId() {
        UUID providedDoctorId = UUID.randomUUID();
        CreateMedicalRecordRequest request = request(UUID.randomUUID());
        request.setDoctorId(providedDoctorId);

        when(accessControl.isAdmin()).thenReturn(true);
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MedicalRecord result = medicalRecordService.createMedicalRecord(request);

        assertEquals(providedDoctorId, result.getDoctorId());
    }

    @Test
    void getMedicalRecordReturnsWhenFound() {
        UUID recordId = UUID.randomUUID();
        MedicalRecord medicalRecord = recordWithId(recordId);

        when(medicalRecordRepository.findByRecordId(recordId)).thenReturn(Optional.of(medicalRecord));

        assertEquals(medicalRecord, medicalRecordService.getMedicalRecord(recordId));
    }

    @Test
    void getMedicalRecordThrowsWhenMissing() {
        UUID recordId = UUID.randomUUID();
        when(medicalRecordRepository.findByRecordId(recordId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> medicalRecordService.getMedicalRecord(recordId));
    }

    @Test
    void getMedicalRecordDeniesWhenNotAuthorized() {
        UUID recordId = UUID.randomUUID();
        MedicalRecord medicalRecord = recordWithId(recordId);
        when(medicalRecordRepository.findByRecordId(recordId)).thenReturn(Optional.of(medicalRecord));
        doThrow(new AccessDeniedException("denied")).when(accessControl).assertCanRead(medicalRecord);

        assertThrows(AccessDeniedException.class, () -> medicalRecordService.getMedicalRecord(recordId));
    }

    @Test
    void searchMedicalRecordsDelegatesWithScopedIds() {
        UUID scopedPatientId = UUID.randomUUID();
        UUID scopedDoctorId = UUID.randomUUID();

        when(accessControl.scopedPatientId(null)).thenReturn(scopedPatientId);
        when(accessControl.scopedDoctorId(null)).thenReturn(scopedDoctorId);
        when(medicalRecordRepository.search(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(recordWithId(UUID.randomUUID()))));

        var result = medicalRecordService.searchMedicalRecords(null, null, null, null, null, Pageable.unpaged());

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void updateMedicalRecordAppliesChangesAndPublishes() {
        UUID recordId = UUID.randomUUID();
        MedicalRecord medicalRecord = recordWithId(recordId);
        when(medicalRecordRepository.findByRecordIdForUpdate(recordId)).thenReturn(Optional.of(medicalRecord));
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMedicalRecordRequest request = new UpdateMedicalRecordRequest();
        request.setDiagnosis("Updated diagnosis");
        request.setNotes("Updated notes");

        MedicalRecord result = medicalRecordService.updateMedicalRecord(recordId, request);

        assertEquals("Updated diagnosis", result.getDiagnosis());
        assertEquals("Updated notes", result.getNotes());
        verify(medicalRecordRepository).save(any(MedicalRecord.class));
        verify(eventPublisher).publishMedicalRecordUpdated(any(MedicalRecord.class));
    }

    @Test
    void updateMedicalRecordDeniesWhenNotAuthorized() {
        UUID recordId = UUID.randomUUID();
        MedicalRecord medicalRecord = recordWithId(recordId);
        when(medicalRecordRepository.findByRecordIdForUpdate(recordId)).thenReturn(Optional.of(medicalRecord));
        doThrow(new AccessDeniedException("denied")).when(accessControl).assertCanWrite(medicalRecord);

        assertThrows(AccessDeniedException.class,
                () -> medicalRecordService.updateMedicalRecord(recordId, new UpdateMedicalRecordRequest()));
    }

    @Test
    void updateMedicalRecordThrowsWhenMissing() {
        UUID recordId = UUID.randomUUID();
        when(medicalRecordRepository.findByRecordIdForUpdate(recordId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> medicalRecordService.updateMedicalRecord(recordId, new UpdateMedicalRecordRequest()));
    }

    @Test
    void updateMedicalRecordRejectsDoctorIdMismatch() {
        UUID recordId = UUID.randomUUID();
        MedicalRecord medicalRecord = recordWithId(recordId);
        when(medicalRecordRepository.findByRecordIdForUpdate(recordId)).thenReturn(Optional.of(medicalRecord));
        when(accessControl.isAdmin()).thenReturn(false);
        when(accessControl.currentUserId()).thenReturn(UUID.randomUUID());

        UpdateMedicalRecordRequest request = new UpdateMedicalRecordRequest();
        request.setDoctorId(UUID.randomUUID());

        assertThrows(AccessDeniedException.class, () -> medicalRecordService.updateMedicalRecord(recordId, request));
    }

    @Test
    void createMedicalRecordRejectsFutureEncounterDate() {
        CreateMedicalRecordRequest request = request(UUID.randomUUID());
        request.setEncounterDate(LocalDate.now().plusDays(1));

        assertThrows(IllegalArgumentException.class,
                () -> medicalRecordService.createMedicalRecord(request));
        verify(medicalRecordRepository, never()).save(any(MedicalRecord.class));
    }

    @Test
    void createMedicalRecordRecordsCreatedHistory() {
        UUID doctorId = UUID.randomUUID();
        CreateMedicalRecordRequest request = request(UUID.randomUUID());
        when(accessControl.isAdmin()).thenReturn(false);
        when(accessControl.currentUserId()).thenReturn(doctorId);
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MedicalRecord result = medicalRecordService.createMedicalRecord(request);

        ArgumentCaptor<MedicalRecordHistory> captor = ArgumentCaptor.forClass(MedicalRecordHistory.class);
        verify(historyRepository).save(captor.capture());
        MedicalRecordHistory history = captor.getValue();
        assertEquals(result.getRecordId(), history.getRecordId());
        assertEquals(MedicalRecordHistoryAction.CREATED, history.getAction());
        assertEquals("doctor1", history.getActor());
        assertEquals(null, history.getChangedFields());
    }

    @Test
    void updateMedicalRecordRecordsChangedFieldNamesOnly() {
        UUID recordId = UUID.randomUUID();
        MedicalRecord medicalRecord = recordWithId(recordId);
        when(medicalRecordRepository.findByRecordIdForUpdate(recordId)).thenReturn(Optional.of(medicalRecord));
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMedicalRecordRequest request = new UpdateMedicalRecordRequest();
        request.setDiagnosis("Updated diagnosis");
        request.setNotes("Updated notes");

        medicalRecordService.updateMedicalRecord(recordId, request);

        ArgumentCaptor<MedicalRecordHistory> captor = ArgumentCaptor.forClass(MedicalRecordHistory.class);
        verify(historyRepository).save(captor.capture());
        MedicalRecordHistory history = captor.getValue();
        assertEquals(MedicalRecordHistoryAction.UPDATED, history.getAction());
        assertEquals("diagnosis,notes", history.getChangedFields());
    }

    @Test
    void updateMedicalRecordSkipsHistoryWhenNothingChanges() {
        UUID recordId = UUID.randomUUID();
        MedicalRecord medicalRecord = recordWithId(recordId);
        when(medicalRecordRepository.findByRecordIdForUpdate(recordId)).thenReturn(Optional.of(medicalRecord));
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMedicalRecordRequest request = new UpdateMedicalRecordRequest();
        request.setChiefComplaint(medicalRecord.getChiefComplaint());
        request.setNotes(medicalRecord.getNotes());
        request.setEncounterDate(medicalRecord.getEncounterDate());

        medicalRecordService.updateMedicalRecord(recordId, request);

        verify(historyRepository, never()).save(any(MedicalRecordHistory.class));
    }

    @Test
    void updateMedicalRecordRejectsFutureEncounterDate() {
        UUID recordId = UUID.randomUUID();
        MedicalRecord medicalRecord = recordWithId(recordId);
        when(medicalRecordRepository.findByRecordIdForUpdate(recordId)).thenReturn(Optional.of(medicalRecord));

        UpdateMedicalRecordRequest request = new UpdateMedicalRecordRequest();
        request.setEncounterDate(LocalDate.now().plusDays(1));

        assertThrows(IllegalArgumentException.class,
                () -> medicalRecordService.updateMedicalRecord(recordId, request));
        verify(medicalRecordRepository, never()).save(any(MedicalRecord.class));
    }

    @Test
    void getMedicalRecordHistoryReusesReadAccess() {
        UUID recordId = UUID.randomUUID();
        MedicalRecord medicalRecord = recordWithId(recordId);
        when(medicalRecordRepository.findByRecordId(recordId)).thenReturn(Optional.of(medicalRecord));
        when(historyRepository.findByRecordIdOrderByOccurredAtAscIdAsc(recordId))
                .thenReturn(List.of(new MedicalRecordHistory()));

        List<MedicalRecordHistory> history = medicalRecordService.getMedicalRecordHistory(recordId);

        assertEquals(1, history.size());
        verify(accessControl).assertCanRead(medicalRecord);
    }

    @Test
    void getMedicalRecordHistoryDeniesWhenNotAuthorized() {
        UUID recordId = UUID.randomUUID();
        MedicalRecord medicalRecord = recordWithId(recordId);
        when(medicalRecordRepository.findByRecordId(recordId)).thenReturn(Optional.of(medicalRecord));
        doThrow(new AccessDeniedException("denied")).when(accessControl).assertCanRead(medicalRecord);

        assertThrows(AccessDeniedException.class,
                () -> medicalRecordService.getMedicalRecordHistory(recordId));
        verify(historyRepository, never()).findByRecordIdOrderByOccurredAtAscIdAsc(any());
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

    private MedicalRecord recordWithId(UUID recordId) {
        MedicalRecord medicalRecord = new MedicalRecord();
        medicalRecord.setRecordId(recordId);
        medicalRecord.setPatientId(UUID.randomUUID());
        medicalRecord.setEncounterDate(LocalDate.of(2026, 8, 10));
        medicalRecord.setChiefComplaint("Routine follow-up");
        medicalRecord.setNotes("Patient reports improvement.");
        return medicalRecord;
    }
}
