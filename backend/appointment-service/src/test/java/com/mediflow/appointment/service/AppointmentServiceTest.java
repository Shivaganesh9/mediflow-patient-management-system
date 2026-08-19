package com.mediflow.appointment.service;

import com.mediflow.appointment.dto.CreateAppointmentRequest;
import com.mediflow.appointment.dto.UpdateAppointmentRequest;
import com.mediflow.appointment.entity.Appointment;
import com.mediflow.appointment.entity.AppointmentStatus;
import com.mediflow.appointment.event.AppointmentEventPublisher;
import com.mediflow.appointment.repository.AppointmentRepository;
import com.mediflow.appointment.security.AccessControlService;
import com.mediflow.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentEventPublisher eventPublisher;

    @Mock
    private AccessControlService accessControl;

    @InjectMocks
    private AppointmentService appointmentService;

    @Test
    void createAppointmentSavesScheduledAppointment() {
        CreateAppointmentRequest request = request(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
        when(appointmentRepository.hasOverlappingAppointment(any(), any(), any(), any())).thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment result = appointmentService.createAppointment(request);

        assertEquals(AppointmentStatus.SCHEDULED, result.getStatus());
        verify(appointmentRepository).save(any(Appointment.class));
        verify(eventPublisher).publishCreated(any(Appointment.class));
    }

    @Test
    void createAppointmentRejectsEndBeforeStart() {
        CreateAppointmentRequest request = request(LocalDateTime.now().plusDays(1).plusHours(1), LocalDateTime.now().plusDays(1));

        assertThrows(IllegalArgumentException.class, () -> appointmentService.createAppointment(request));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void createAppointmentRejectsOverlappingSlot() {
        CreateAppointmentRequest request = request(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
        when(appointmentRepository.hasOverlappingAppointment(any(), any(), any(), any())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> appointmentService.createAppointment(request));
        verify(appointmentRepository, never()).save(any(Appointment.class));
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
    void updateAppointmentReschedulesAndPublishes() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));
        when(appointmentRepository.hasOverlappingAppointmentForUpdate(any(), any(), any(), any(), any())).thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateAppointmentRequest request = new UpdateAppointmentRequest();
        request.setStartTime(LocalDateTime.now().plusDays(2));
        request.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));

        Appointment result = appointmentService.updateAppointment(appointmentId, request);

        assertEquals(request.getStartTime(), result.getStartTime());
        assertEquals(request.getEndTime(), result.getEndTime());
        verify(eventPublisher).publishUpdated(any(Appointment.class));
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

        assertThrows(IllegalArgumentException.class, () -> appointmentService.updateAppointment(appointmentId, request));
    }

    @Test
    void updateAppointmentRejectsImmutableStatus() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.CANCELLED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));

        UpdateAppointmentRequest request = new UpdateAppointmentRequest();
        request.setStartTime(LocalDateTime.now().plusDays(2));
        request.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));

        assertThrows(IllegalArgumentException.class, () -> appointmentService.updateAppointment(appointmentId, request));
    }

    @Test
    void cancelAppointmentCancelsAndPublishes() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment result = appointmentService.cancelAppointment(appointmentId);

        assertEquals(AppointmentStatus.CANCELLED, result.getStatus());
        verify(eventPublisher).publishCancelled(any(Appointment.class));
    }

    @Test
    void cancelAppointmentRejectsImmutableStatus() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.COMPLETED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> appointmentService.cancelAppointment(appointmentId));
    }

    @Test
    void createAppointmentScopesIdentitiesThroughAccessControl() {
        UUID scopedPatientId = UUID.randomUUID();
        UUID scopedDoctorId = UUID.randomUUID();
        when(accessControl.scopedPatientId(any())).thenReturn(scopedPatientId);
        when(accessControl.scopedDoctorId(any())).thenReturn(scopedDoctorId);
        when(appointmentRepository.hasOverlappingAppointment(any(), any(), any(), any())).thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void cancelAppointmentDeniedByAccessControl() {
        UUID appointmentId = UUID.randomUUID();
        Appointment existing = existingAppointment(appointmentId, AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findByAppointmentIdForUpdate(appointmentId)).thenReturn(Optional.of(existing));
        doThrow(new AccessDeniedException("denied")).when(accessControl).assertCanAccess(existing);

        assertThrows(AccessDeniedException.class, () -> appointmentService.cancelAppointment(appointmentId));
        verify(appointmentRepository, never()).save(any(Appointment.class));
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
        appointment.setStatus(status);
        return appointment;
    }
}
