package com.mediflow.appointment.service;

import com.mediflow.appointment.dto.CreateAppointmentRequest;
import com.mediflow.appointment.dto.UpdateAppointmentRequest;
import com.mediflow.appointment.entity.Appointment;
import com.mediflow.appointment.entity.AppointmentStatus;
import com.mediflow.appointment.event.AppointmentEventPublisher;
import com.mediflow.appointment.repository.AppointmentRepository;
import com.mediflow.appointment.security.AccessControlService;
import com.mediflow.common.exception.ConflictException;
import com.mediflow.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Appointment booking, rescheduling and cancellation.
 *
 * <p>Slot conflicts are rejected twice: a friendly pre-check keeps the common case cheap and
 * returns a precise message, and the {@code ex_appointments_doctor_slot} /
 * {@code ex_appointments_patient_slot} exclusion constraints added in
 * {@code V6__add_appointment_slot_exclusion.sql} are the authority for concurrent requests. A
 * request that loses that race surfaces as {@link ConflictException} (HTTP 409) instead of a
 * duplicate booking.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    /** SQLSTATE raised by PostgreSQL when an exclusion constraint rejects a row. */
    private static final String EXCLUSION_VIOLATION = "23P01";

    private static final String DOCTOR_SLOT_CONSTRAINT = "ex_appointments_doctor_slot";
    private static final String PATIENT_SLOT_CONSTRAINT = "ex_appointments_patient_slot";

    private static final String DOCTOR_SLOT_CONFLICT_MESSAGE =
            "The doctor already has an appointment that overlaps this time slot";
    private static final String PATIENT_SLOT_CONFLICT_MESSAGE =
            "The patient already has an appointment that overlaps this time slot";

    private final AppointmentRepository appointmentRepository;
    private final AppointmentEventPublisher eventPublisher;
    private final AccessControlService accessControl;

    @Transactional
    public Appointment createAppointment(CreateAppointmentRequest request) {
        validateTimeRange(request.getStartTime(), request.getEndTime());
        UUID patientId = accessControl.scopedPatientId(request.getPatientId());
        UUID doctorId = accessControl.scopedDoctorId(request.getDoctorId());

        if (appointmentRepository.hasOverlappingAppointment(
                patientId, doctorId, request.getStartTime(), request.getEndTime())) {
            throw new ConflictException(
                    "Appointment conflicts with an existing appointment for the patient or doctor");
        }

        Appointment appointment = new Appointment();
        appointment.setPatientId(patientId);
        appointment.setDoctorId(doctorId);
        appointment.setStartTime(request.getStartTime());
        appointment.setEndTime(request.getEndTime());
        appointment.setNotes(request.getNotes());
        appointment.setReason(request.getReason());
        appointment.setAppointmentType(request.getAppointmentType());
        appointment.setStatus(AppointmentStatus.SCHEDULED);

        // Flush inside the transaction so a slot race is rejected here, where it can be reported
        // as a precise 409, instead of surfacing at commit as a generic constraint violation.
        appointment = saveAndTranslateSlotConflict(appointment);
        eventPublisher.publishCreated(appointment);
        return appointment;
    }

    @Transactional(readOnly = true)
    public Page<Appointment> searchAppointments(AppointmentStatus status, UUID patientId, UUID doctorId,
                                                LocalDateTime fromTime, LocalDateTime toTime, Pageable pageable) {
        UUID scopedPatientId = accessControl.scopedPatientId(patientId);
        UUID scopedDoctorId = accessControl.scopedDoctorId(doctorId);
        return appointmentRepository.search(status, scopedPatientId, scopedDoctorId, fromTime, toTime, pageable);
    }

    @Transactional(readOnly = true)
    public Appointment getAppointment(UUID appointmentId) {
        Appointment appointment = appointmentRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with id: " + appointmentId));
        accessControl.assertCanAccess(appointment);
        return appointment;
    }

    @Transactional
    public Appointment updateAppointment(UUID appointmentId, UpdateAppointmentRequest request) {
        Appointment appointment = appointmentRepository.findByAppointmentIdForUpdate(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with id: " + appointmentId));
        accessControl.assertCanAccess(appointment);

        if (!isMutable(appointment.getStatus())) {
            throw new ConflictException(
                    "Appointment cannot be rescheduled in status " + appointment.getStatus());
        }

        validateTimeRange(request.getStartTime(), request.getEndTime());
        if (appointmentRepository.hasOverlappingAppointmentForUpdate(
                appointmentId, appointment.getPatientId(), appointment.getDoctorId(),
                request.getStartTime(), request.getEndTime())) {
            throw new ConflictException(
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

        appointment = saveAndTranslateSlotConflict(appointment);
        eventPublisher.publishUpdated(appointment);
        return appointment;
    }

    @Transactional
    public Appointment cancelAppointment(UUID appointmentId) {
        Appointment appointment = appointmentRepository.findByAppointmentIdForUpdate(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with id: " + appointmentId));
        accessControl.assertCanAccess(appointment);

        if (!isMutable(appointment.getStatus())) {
            throw new ConflictException(
                    "Appointment cannot be cancelled in status " + appointment.getStatus());
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment = appointmentRepository.saveAndFlush(appointment);
        eventPublisher.publishCancelled(appointment);
        return appointment;
    }

    /**
     * Persists the appointment and maps an exclusion-constraint rejection to a 409.
     *
     * <p>Only a slot conflict is translated; any other integrity violation is rethrown unchanged so
     * the shared handler keeps reporting it as a generic conflict.</p>
     */
    private Appointment saveAndTranslateSlotConflict(Appointment appointment) {
        try {
            return appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException ex) {
            if (isExclusionViolation(ex)) {
                String message = conflictMessageFor(ex);
                log.debug("Slot exclusion constraint rejected appointment {}: {}",
                        appointment.getAppointmentId(), message);
                throw new ConflictException(message);
            }
            throw ex;
        }
    }

    private boolean isExclusionViolation(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof SQLException sqlException
                    && EXCLUSION_VIOLATION.equals(sqlException.getSQLState())) {
                return true;
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }
        return false;
    }

    /**
     * Chooses the message that matches the violated constraint. PostgreSQL reports the constraint
     * name in the exception text, so a patient/doctor collision is distinguishable without an extra
     * query. Unknown constraints fall back to the doctor message (the original constraint).
     */
    private String conflictMessageFor(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            String text = cause.getMessage();
            if (text != null) {
                if (text.contains(PATIENT_SLOT_CONSTRAINT)) {
                    return PATIENT_SLOT_CONFLICT_MESSAGE;
                }
                if (text.contains(DOCTOR_SLOT_CONSTRAINT)) {
                    return DOCTOR_SLOT_CONFLICT_MESSAGE;
                }
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }
        return DOCTOR_SLOT_CONFLICT_MESSAGE;
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
