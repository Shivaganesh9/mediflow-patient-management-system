package com.mediflow.appointment.service;

import com.mediflow.appointment.dto.CreateAppointmentRequest;
import com.mediflow.appointment.dto.UpdateAppointmentRequest;
import com.mediflow.appointment.entity.Appointment;
import com.mediflow.appointment.entity.AppointmentHistory;
import com.mediflow.appointment.entity.AppointmentHistoryAction;
import com.mediflow.appointment.entity.AppointmentStatus;
import com.mediflow.appointment.event.AppointmentEventPublisher;
import com.mediflow.appointment.repository.AppointmentHistoryRepository;
import com.mediflow.appointment.repository.AppointmentRepository;
import com.mediflow.appointment.schedule.DoctorScheduleValidator;
import com.mediflow.appointment.security.AccessControlService;
import com.mediflow.common.exception.ConflictException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.common.exception.ServiceUnavailableException;
import com.mediflow.common.time.ClinicClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentHistoryRepository historyRepository;

    @Mock
    private AppointmentEventPublisher eventPublisher;

    @Mock
    private AccessControlService accessControl;

    @Mock
    private DoctorScheduleValidator doctorScheduleValidator;

    @Mock
    private ClinicClock clinicClock;

    @InjectMocks
    private AppointmentService appointmentService;

    @BeforeEach
    void setUp() {
        lenient().when(clinicClock.now()).thenReturn(LocalDateTime.now());
        lenient().when(clinicClock.instant()).thenReturn(Instant.now());
        lenient().when(accessControl.currentUsername()).thenReturn("tester");
    }

    @Test
    void createAppointmentSavesScheduledAppointment() {
        CreateAppointmentRequest request = request(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
        when(appointmentRepository.hasOverlappingAppointment(any(), any(), any(), any())).thenReturn(false);
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment result = appointmentService.createAppointment(request);

        assertEquals(AppointmentStatus.SCHEDULED, result.getStatus());
        verify(doctorScheduleValidator).validateSlot(eq(result.getDoctorId()),
                eq(request.getStartTime()), eq(request.getEndTime()));
        verify(appointmentRepository).saveAndFlush(any(Appointment.class));
        verify(eventPublisher).publishCreated(any(Appointment.class));
    }

    @Test
    void createAppointmentRecordsCreatedHistory() {
        CreateAppointmentRequest request = request(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
        when(appointmentRepository.hasOverlappingAppointment(any(), any(), any(), any())).thenReturn(false);
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment result = appointmentService.createAppointment(request);

        AppointmentHistory history = captureHistory();
        assertEquals(AppointmentHistoryAction.CREATED, history.getAction());
        assertEquals(null, history.getFromStatus());
        assertEquals(AppointmentStatus.SCHEDULED, history.getToStatus());
        assertEquals(result.getAppointmentId(), history.getAppointmentId());
        assertEquals("tester", history.getActor());
    }

    @Test
    void createAppointmentRejectsEndBeforeStart() {
        CreateAppointmentRequest request = request(LocalDateTime.now().plusDays(1).plusHours(1), LocalDateTime.now().plusDays(1));

        assertThrows(IllegalArgumentException.class, () -> appointmentService.createAppointment(request));
        verify(doctorScheduleValidator, never()).validateSlot(any(), any(), any());
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void createAppointmentRejectsPastSlot() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        CreateAppointmentRequest request = request(start, start.plusHours(1));

        assertThrows(IllegalArgumentException.class, () -> appointmentService.createAppointment(request));
        verify(doctorScheduleValidator, never()).validateSlot(any(), any(), any());
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void createAppointmentRejectsSlotOutsideWorkingHours() {
        CreateAppointmentRequest request = request(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
        doThrow(new ConflictException("The requested slot is outside the doctor's working hours"))
                .when(doctorScheduleValidator).validateSlot(any(), any(), any());

        ConflictException conflict = assertThrows(ConflictException.class,
                () -> appointmentService.createAppointment(request));

        assertTrue(conflict.getMessage().contains("working hours"));
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void createAppointmentFailsClosedWhenAvailabilityCannotBeChecked() {
        CreateAppointmentRequest request = request(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
        doThrow(new ServiceUnavailableException("availability unavailable"))
                .when(doctorScheduleValidator).validateSlot(any(), any(), any());

        assertThrows(ServiceUnavailableException.class, () -> appointmentService.createAppointment(request));
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
        verify(eventPublisher, never()).publishCreated(any(Appointment.class));
    }

    @Test
    void createAppointmentRejectsOverlappingSlot() {
        CreateAppointmentRequest request = request(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
        when(appointmentRepository.hasOverlappingAppointment(any(), any(), any(), any())).thenReturn(true);

        assertThrows(ConflictException.class, () -> appointmentService.createAppointment(request));
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void createAppointmentTranslatesDoctorExclusionViolationToConflict() {
        // The pre-check passes (the slot looked free) and the database is the authority: PostgreSQL
        // rejected the insert with SQLSTATE 23P01 because a concurrent request took the slot first.
        CreateAppointmentRequest request = request(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
        when(appointmentRepository.hasOverlappingAppointment(any(), any(), any(), any())).thenReturn(false);
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenThrow(new DataIntegrityViolationException("could not execute statement",
                        new SQLException("conflicting key value violates exclusion constraint "
                                + "\"ex_appointments_doctor_slot\"", "23P01")));

        ConflictException conflict = assertThrows(ConflictException.class,
                () -> appointmentService.createAppointment(request));

        assertTrue(conflict.getMessage().contains("doctor already has an appointment"));
        verify(eventPublisher, never()).publishCreated(any(Appointment.class));
    }

    @Test
    void createAppointmentTranslatesPatientExclusionViolationToConflict() {
        // A different doctor but the same patient: the patient slot constraint is the one that fires.
        CreateAppointmentRequest request = request(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
        when(appointmentRepository.hasOverlappingAppointment(any(), any(), any(), any())).thenReturn(false);
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenThrow(new DataIntegrityViolationException("could not execute statement",
                        new SQLException("conflicting key value violates exclusion constraint "
                                + "\"ex_appointments_patient_slot\"", "23P01")));

        ConflictException conflict = assertThrows(ConflictException.class,
                () -> appointmentService.createAppointment(request));

        assertTrue(conflict.getMessage().contains("patient already has an appointment"));
        verify(eventPublisher, never()).publishCreated(any(Appointment.class));
    }

    @Test
    void createAppointmentTranslatesConcurrentBookingDeadlockToConflict() {
        // Two inserts that check the doctor and patient constraints in opposite order can deadlock
        // (SQLSTATE 40P01). PostgreSQL aborts one transaction; the caller must see 409, not 500.
        CreateAppointmentRequest request = request(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
        when(appointmentRepository.hasOverlappingAppointment(any(), any(), any(), any())).thenReturn(false);
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenThrow(new CannotAcquireLockException("could not execute statement",
                        new SQLException("deadlock detected", "40P01")));

        ConflictException conflict = assertThrows(ConflictException.class,
                () -> appointmentService.createAppointment(request));

        assertTrue(conflict.getMessage().contains("please retry"));
        verify(eventPublisher, never()).publishCreated(any(Appointment.class));
    }

    @Test
    void createAppointmentRethrowsUnrelatedIntegrityViolations() {
        CreateAppointmentRequest request = request(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
        when(appointmentRepository.hasOverlappingAppointment(any(), any(), any(), any())).thenReturn(false);
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate appointment id"));

        assertThrows(DataIntegrityViolationException.class,
                () -> appointmentService.createAppointment(request));
    }

    @Test
    void getAppointmentReturnsWhenFound() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        when(appointmentRepository.findByAppointmentId(appointmentId)).thenReturn(Optional.of(appointment));

        assertEquals(appointment, appointmentService.getAppointment(appointmentId));
    }

    @Test
    void getAppointmentThrowsWhenMissing() {
        UUID appointmentId = UUID.randomUUID();
        when(appointmentRepository.findByAppointmentId(appointmentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> appointmentService.getAppointment(appointmentId));
    }

    @Test
    void getAppointmentHistoryReusesAppointmentAccessRules() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = existingAppointment(appointmentId, AppointmentStatus.COMPLETED);
        when(appointmentRepository.findByAppointmentId(appointmentId)).thenReturn(Optional.of(appointment));
        when(historyRepository.findByAppointmentIdOrderByOccurredAtAscIdAsc(appointmentId))
                .thenReturn(List.of(new AppointmentHistory()));

        List<AppointmentHistory> history = appointmentService.getAppointmentHistory(appointmentId);

        assertEquals(1, history.size());
        verify(accessControl).assertCanAccess(appointment);
    }

    @Test
    void getAppointmentHistoryDeniedByAccessControl() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = existingAppointment(appointmentId, AppointmentStatus.COMPLETED);
        when(appointmentRepository.findByAppointmentId(appointmentId)).thenReturn(Optional.of(appointment));
        doThrow(new AccessDeniedException("denied")).when(accessControl).assertCanAccess(appointment);

        assertThrows(AccessDeniedException.class,
                () -> appointmentService.getAppointmentHistory(appointmentId));
        verify(historyRepository, never()).findByAppointmentIdOrderByOccurredAtAscIdAsc(any());
    }

    @Test
    void updateAppointmentReschedulesAndPublishes() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));
        when(appointmentRepository.hasOverlappingAppointmentForUpdate(any(), any(), any(), any(), any())).thenReturn(false);
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateAppointmentRequest request = new UpdateAppointmentRequest();
        request.setStartTime(LocalDateTime.now().plusDays(2));
        request.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));

        Appointment result = appointmentService.updateAppointment(appointmentId, request);

        assertEquals(request.getStartTime(), result.getStartTime());
        assertEquals(request.getEndTime(), result.getEndTime());
        verify(doctorScheduleValidator).validateSlot(eq(existing.getDoctorId()),
                eq(request.getStartTime()), eq(request.getEndTime()));
        verify(eventPublisher).publishUpdated(any(Appointment.class));
    }

    @Test
    void updateAppointmentRecordsRescheduleHistoryWhenTheSlotChanges() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));
        when(appointmentRepository.hasOverlappingAppointmentForUpdate(any(), any(), any(), any(), any())).thenReturn(false);
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateAppointmentRequest request = new UpdateAppointmentRequest();
        request.setStartTime(existing.getStartTime().plusDays(1));
        request.setEndTime(existing.getEndTime().plusDays(1));

        appointmentService.updateAppointment(appointmentId, request);

        AppointmentHistory history = captureHistory();
        assertEquals(AppointmentHistoryAction.RESCHEDULED, history.getAction());
        assertEquals(AppointmentStatus.CONFIRMED, history.getFromStatus());
        assertTrue(history.getDetails().contains("Rescheduled from"));
    }

    @Test
    void updateAppointmentDoesNotRecordARescheduleForMetadataOnlyChanges() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));
        when(appointmentRepository.hasOverlappingAppointmentForUpdate(any(), any(), any(), any(), any())).thenReturn(false);
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateAppointmentRequest request = new UpdateAppointmentRequest();
        request.setStartTime(existing.getStartTime());
        request.setEndTime(existing.getEndTime());
        request.setNotes("Bring previous reports");

        appointmentService.updateAppointment(appointmentId, request);

        verify(historyRepository, never()).save(any(AppointmentHistory.class));
    }

    @Test
    void updateAppointmentRejectsPastSlot() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));

        UpdateAppointmentRequest request = new UpdateAppointmentRequest();
        request.setStartTime(LocalDateTime.now().minusDays(1));
        request.setEndTime(LocalDateTime.now().minusDays(1).plusHours(1));

        assertThrows(IllegalArgumentException.class,
                () -> appointmentService.updateAppointment(appointmentId, request));
        verify(doctorScheduleValidator, never()).validateSlot(any(), any(), any());
    }

    @Test
    void updateAppointmentRejectsOverlappingSlot() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));
        when(appointmentRepository.hasOverlappingAppointmentForUpdate(any(), any(), any(), any(), any())).thenReturn(true);

        UpdateAppointmentRequest request = new UpdateAppointmentRequest();
        request.setStartTime(LocalDateTime.now().plusDays(2));
        request.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));

        assertThrows(ConflictException.class, () -> appointmentService.updateAppointment(appointmentId, request));
    }

    @Test
    void updateAppointmentRejectsImmutableStatus() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.CANCELLED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));

        UpdateAppointmentRequest request = new UpdateAppointmentRequest();
        request.setStartTime(LocalDateTime.now().plusDays(2));
        request.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));

        assertThrows(ConflictException.class, () -> appointmentService.updateAppointment(appointmentId, request));
    }

    @Test
    void cancelAppointmentCancelsPublishesAndRecordsHistory() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment result = appointmentService.cancelAppointment(appointmentId);

        assertEquals(AppointmentStatus.CANCELLED, result.getStatus());
        AppointmentHistory history = captureHistory();
        assertEquals(AppointmentHistoryAction.CANCELLED, history.getAction());
        assertEquals(AppointmentStatus.CONFIRMED, history.getFromStatus());
        verify(eventPublisher).publishCancelled(any(Appointment.class));
    }

    @Test
    void cancelAppointmentRejectsImmutableStatus() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.COMPLETED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class, () -> appointmentService.cancelAppointment(appointmentId));
    }

    @Test
    void confirmAppointmentMovesScheduledToConfirmed() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment result = appointmentService.confirmAppointment(appointmentId);

        assertEquals(AppointmentStatus.CONFIRMED, result.getStatus());
        AppointmentHistory history = captureHistory();
        assertEquals(AppointmentHistoryAction.CONFIRMED, history.getAction());
        assertEquals(AppointmentStatus.SCHEDULED, history.getFromStatus());
        verify(eventPublisher).publishUpdated(any(Appointment.class));
    }

    @Test
    void confirmAppointmentRejectsAnAlreadyConfirmedAppointment() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class, () -> appointmentService.confirmAppointment(appointmentId));
        verify(historyRepository, never()).save(any(AppointmentHistory.class));
    }

    @Test
    void startAppointmentRequiresAConfirmedAppointment() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class, () -> appointmentService.startAppointment(appointmentId));
    }

    @Test
    void startAppointmentMovesConfirmedToInProgressForClinicalStaff() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment result = appointmentService.startAppointment(appointmentId);

        assertEquals(AppointmentStatus.IN_PROGRESS, result.getStatus());
        verify(accessControl).assertClinicalAccess();
        assertEquals(AppointmentHistoryAction.STARTED, captureHistory().getAction());
    }

    @Test
    void startAppointmentIsDeniedWithoutClinicalAccess() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));
        doThrow(new AccessDeniedException("denied")).when(accessControl).assertClinicalAccess();

        assertThrows(AccessDeniedException.class, () -> appointmentService.startAppointment(appointmentId));
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void completeAppointmentRequiresInProgress() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class, () -> appointmentService.completeAppointment(appointmentId));
    }

    @Test
    void completeAppointmentMovesInProgressToCompleted() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.IN_PROGRESS);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment result = appointmentService.completeAppointment(appointmentId);

        assertEquals(AppointmentStatus.COMPLETED, result.getStatus());
        assertEquals(AppointmentHistoryAction.COMPLETED, captureHistory().getAction());
    }

    @Test
    void markNoShowAcceptsScheduledAndConfirmed() {
        UUID scheduledId = UUID.randomUUID();
        Appointment scheduled = existingAppointment(scheduledId, AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findByAppointmentIdForUpdate(scheduledId)).thenReturn(Optional.of(scheduled));
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(AppointmentStatus.NO_SHOW, appointmentService.markNoShow(scheduledId).getStatus());

        UUID confirmedId = UUID.randomUUID();
        Appointment confirmed = existingAppointment(confirmedId, AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findByAppointmentIdForUpdate(confirmedId)).thenReturn(Optional.of(confirmed));

        assertEquals(AppointmentStatus.NO_SHOW, appointmentService.markNoShow(confirmedId).getStatus());
    }

    @Test
    void markNoShowRejectsCompletedAppointment() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.COMPLETED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class, () -> appointmentService.markNoShow(appointmentId));
    }

    @Test
    void createAppointmentScopesIdentitiesThroughAccessControl() {
        UUID scopedPatientId = UUID.randomUUID();
        UUID scopedDoctorId = UUID.randomUUID();
        when(accessControl.scopedPatientId(any())).thenReturn(scopedPatientId);
        when(accessControl.scopedDoctorId(any())).thenReturn(scopedDoctorId);
        when(appointmentRepository.hasOverlappingAppointment(any(), any(), any(), any())).thenReturn(false);
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment result = appointmentService.createAppointment(
                request(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1)));

        assertEquals(scopedPatientId, result.getPatientId());
        assertEquals(scopedDoctorId, result.getDoctorId());
        verify(accessControl).scopedPatientId(any());
        verify(accessControl).scopedDoctorId(any());
    }

    @Test
    void getAppointmentDeniedByAccessControl() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = existingAppointment(appointmentId, AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findByAppointmentId(appointmentId)).thenReturn(Optional.of(appointment));
        doThrow(new AccessDeniedException("denied")).when(accessControl).assertCanAccess(appointment);

        assertThrows(AccessDeniedException.class, () -> appointmentService.getAppointment(appointmentId));
    }

    @Test
    void updateAppointmentDeniedByAccessControl() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));
        doThrow(new AccessDeniedException("denied")).when(accessControl).assertCanAccess(existing);

        UpdateAppointmentRequest request = new UpdateAppointmentRequest();
        request.setStartTime(LocalDateTime.now().plusDays(2));
        request.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));

        assertThrows(AccessDeniedException.class, () -> appointmentService.updateAppointment(appointmentId, request));
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void cancelAppointmentDeniedByAccessControl() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));
        doThrow(new AccessDeniedException("denied")).when(accessControl).assertCanAccess(existing);

        assertThrows(AccessDeniedException.class, () -> appointmentService.cancelAppointment(appointmentId));
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void searchAppointmentsForcesScopesThroughAccessControl() {
        UUID scopedPatientId = UUID.randomUUID();
        UUID scopedDoctorId = UUID.randomUUID();
        when(accessControl.scopedPatientId(any())).thenReturn(scopedPatientId);
        when(accessControl.scopedDoctorId(any())).thenReturn(scopedDoctorId);
        when(appointmentRepository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));

        appointmentService.searchAppointments(null, UUID.randomUUID(), UUID.randomUUID(),
                null, null, org.springframework.data.domain.PageRequest.of(0, 20));

        verify(accessControl).scopedPatientId(any());
        verify(accessControl).scopedDoctorId(any());
        verify(appointmentRepository).search(any(), org.mockito.ArgumentMatchers.eq(scopedPatientId),
                org.mockito.ArgumentMatchers.eq(scopedDoctorId), any(), any(), any());
    }

    private AppointmentHistory captureHistory() {
        ArgumentCaptor<AppointmentHistory> captor = ArgumentCaptor.forClass(AppointmentHistory.class);
        verify(historyRepository).save(captor.capture());
        return captor.getValue();
    }

    private CreateAppointmentRequest request(LocalDateTime startTime, LocalDateTime endTime) {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setPatientId(UUID.randomUUID());
        request.setDoctorId(UUID.randomUUID());
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setReason("Checkup");
        return request;
    }

    private Appointment existingAppointment(UUID appointmentId, AppointmentStatus status) {
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(appointmentId);
        appointment.setPatientId(UUID.randomUUID());
        appointment.setDoctorId(UUID.randomUUID());
        appointment.setStartTime(LocalDateTime.now().plusDays(3));
        appointment.setEndTime(LocalDateTime.now().plusDays(3).plusHours(1));
        appointment.setStatus(status);
        return appointment;
    }
}
