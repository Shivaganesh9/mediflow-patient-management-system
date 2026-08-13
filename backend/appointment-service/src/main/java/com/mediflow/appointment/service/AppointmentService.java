package com.mediflow.appointment.service;

import com.mediflow.appointment.dto.CreateAppointmentRequest;
import com.mediflow.appointment.dto.UpdateAppointmentRequest;
import com.mediflow.appointment.entity.Appointment;
import com.mediflow.appointment.entity.AppointmentStatus;
import com.mediflow.appointment.event.AppointmentEventPublisher;
import com.mediflow.appointment.repository.AppointmentRepository;
import com.mediflow.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentEventPublisher eventPublisher;

    @Transactional
    public Appointment createAppointment(CreateAppointmentRequest request) {
        validateTimeRange(request.getStartTime(), request.getEndTime());
        if (appointmentRepository.hasOverlappingAppointment(
                request.getPatientId(), request.getDoctorId(), request.getStartTime(), request.getEndTime())) {
            throw new IllegalArgumentException(
                    "Appointment conflicts with an existing appointment for the patient or doctor");
        }

        Appointment appointment = new Appointment();
        appointment.setPatientId(request.getPatientId());
        appointment.setDoctorId(request.getDoctorId());
        appointment.setStartTime(request.getStartTime());
        appointment.setEndTime(request.getEndTime());
        appointment.setNotes(request.getNotes());
        appointment.setReason(request.getReason());
        appointment.setAppointmentType(request.getAppointmentType());
        appointment.setStatus(AppointmentStatus.SCHEDULED);

        appointment = appointmentRepository.save(appointment);
        eventPublisher.publishCreated(appointment);
        return appointment;
    }

    @Transactional(readOnly = true)
    public Page<Appointment> searchAppointments(AppointmentStatus status, UUID patientId, UUID doctorId,
                                                LocalDateTime fromTime, LocalDateTime toTime, Pageable pageable) {
        return appointmentRepository.search(status, patientId, doctorId, fromTime, toTime, pageable);
    }

    @Transactional(readOnly = true)
    public Appointment getAppointment(UUID appointmentId) {
        return appointmentRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with id: " + appointmentId));
    }

    @Transactional
    public Appointment updateAppointment(UUID appointmentId, UpdateAppointmentRequest request) {
        Appointment appointment = appointmentRepository.findByAppointmentIdForUpdate(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with id: " + appointmentId));

        if (!isMutable(appointment.getStatus())) {
            throw new IllegalArgumentException(
                    "Appointment cannot be rescheduled in status " + appointment.getStatus());
        }

        validateTimeRange(request.getStartTime(), request.getEndTime());
        if (appointmentRepository.hasOverlappingAppointmentForUpdate(
                appointmentId, appointment.getPatientId(), appointment.getDoctorId(),
                request.getStartTime(), request.getEndTime())) {
            throw new IllegalArgumentException(
                    "Appointment conflicts with an existing appointment for the patient or doctor");
        }

        appointment.setStartTime(request.getStartTime());
        appointment.setEndTime(request.getEndTime());
        if (request.getNotes() != null) {
            appointment.setNotes(request.getNotes());
        }
        if (request.getReason() != null) {
            appointment.setReason(request.getReason());
        }
        if (request.getAppointmentType() != null) {
            appointment.setAppointmentType(request.getAppointmentType());
        }

        appointment = appointmentRepository.save(appointment);
        eventPublisher.publishUpdated(appointment);
        return appointment;
    }

    @Transactional
    public Appointment cancelAppointment(UUID appointmentId) {
        Appointment appointment = appointmentRepository.findByAppointmentIdForUpdate(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with id: " + appointmentId));

        if (!isMutable(appointment.getStatus())) {
            throw new IllegalArgumentException(
                    "Appointment cannot be cancelled in status " + appointment.getStatus());
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment = appointmentRepository.save(appointment);
        eventPublisher.publishCancelled(appointment);
        return appointment;
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
    }

    private boolean isMutable(AppointmentStatus status) {
        return status == AppointmentStatus.SCHEDULED || status == AppointmentStatus.CONFIRMED;
    }
}
