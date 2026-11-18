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
import com.mediflow.common.time.ClinicClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Appointment booking, rescheduling, lifecycle and history.
 *
 * <p>Slot conflicts are rejected twice: a friendly pre-check keeps the common case cheap and
 * returns a precise message, and the {@code ex_appointments_doctor_slot} /
 * {@code ex_appointments_patient_slot} exclusion constraints added in
 * {@code V6__add_appointment_slot_exclusion.sql} are the authority for concurrent requests. A
 * request that loses that race surfaces as {@link ConflictException} (HTTP 409) instead of a
 * duplicate booking.</p>
 *
 * <p>Bookings and reschedules additionally require the doctor to be working that slot: the
 * synchronous schedule check rejects times outside the doctor's active availability windows or on
 * approved leave. Appointment times are interpreted in the configured clinic zone.</p>
 *
 * <p>Every lifecycle change writes an append-only {@link AppointmentHistory} row in the same
 * transaction, so the audit trail can never disagree with the appointment state.</p>
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

    private static final Set<AppointmentStatus> CONFIRMABLE = Set.of(AppointmentStatus.SCHEDULED);
    private static final Set<AppointmentStatus> STARTABLE = Set.of(AppointmentStatus.CONFIRMED);
    private static final Set<AppointmentStatus> COMPLETABLE = Set.of(AppointmentStatus.IN_PROGRESS);
    private static final Set<AppointmentStatus> NO_SHOWABLE =
            Set.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED);

    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository historyRepository;
    private final AppointmentEventPublisher eventPublisher;
    private final AccessControlService accessControl;
    private final DoctorScheduleValidator doctorScheduleValidator;
    private final ClinicClock clinicClock;

    @Transactional
    public Appointment createAppointment(CreateAppointmentRequest request) {
        validateTimeRange(request.getStartTime(), request.getEndTime());
        requireFuture(request.getStartTime());
        UUID patientId = accessControl.scopedPatientId(request.getPatientId());
        UUID doctorId = accessControl.scopedDoctorId(request.getDoctorId());

        doctorScheduleValidator.validateSlot(doctorId, request.getStartTime(), request.getEndTime());

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
        appointment.setCreatedBy(accessControl.currentUsername());

        // Flush inside the transaction so a slot race is rejected here, where it can be reported
        // as a precise 409, instead of surfacing at commit as a generic constraint violation.
        appointment = saveAndTranslateSlotConflict(appointment);
        recordHistory(appointment, AppointmentHistoryAction.CREATED, null, null);
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

    @Transactional(readOnly = true)
    public List<AppointmentHistory> getAppointmentHistory(UUID appointmentId) {
        // Reuses the appointment access rules: patients see only their own history, doctors only
        // their own appointments, staff the appointments they may already read.
        getAppointment(appointmentId);
        return historyRepository.findByAppointmentIdOrderByOccurredAtAscIdAsc(appointmentId);
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
        requireFuture(request.getStartTime());
        doctorScheduleValidator.validateSlot(
                appointment.getDoctorId(), request.getStartTime(), request.getEndTime());

        if (appointmentRepository.hasOverlappingAppointmentForUpdate(
                appointmentId, appointment.getPatientId(), appointment.getDoctorId(),
                request.getStartTime(), request.getEndTime())) {
            throw new ConflictException(
                    "Appointment conflicts with an existing appointment for the patient or doctor");
        }

        LocalDateTime previousStart = appointment.getStartTime();
        LocalDateTime previousEnd = appointment.getEndTime();
        boolean slotChanged = !previousStart.equals(request.getStartTime())
                || !previousEnd.equals(request.getEndTime());

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
        appointment.setUpdatedBy(accessControl.currentUsername());

        appointment = saveAndTranslateSlotConflict(appointment);
        if (slotChanged) {
            recordHistory(appointment, AppointmentHistoryAction.RESCHEDULED, appointment.getStatus(),
                    "Rescheduled from " + previousStart + " to " + request.getStartTime());
        }
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

        AppointmentStatus previous = appointment.getStatus();
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setUpdatedBy(accessControl.currentUsername());
        appointment = appointmentRepository.saveAndFlush(appointment);
        recordHistory(appointment, AppointmentHistoryAction.CANCELLED, previous, null);
        eventPublisher.publishCancelled(appointment);
        return appointment;
    }

    /**
     * Confirms a scheduled booking. Any caller who may access the appointment (the patient, the
     * doctor, or staff) can confirm it; only clinical roles may move it further.
     */
    @Transactional
    public Appointment confirmAppointment(UUID appointmentId) {
        return transition(appointmentId, AppointmentStatus.CONFIRMED,
                AppointmentHistoryAction.CONFIRMED, CONFIRMABLE, "confirmed", false);
    }

    @Transactional
    public Appointment startAppointment(UUID appointmentId) {
        return transition(appointmentId, AppointmentStatus.IN_PROGRESS,
                AppointmentHistoryAction.STARTED, STARTABLE, "started", true);
    }

    @Transactional
    public Appointment completeAppointment(UUID appointmentId) {
        return transition(appointmentId, AppointmentStatus.COMPLETED,
                AppointmentHistoryAction.COMPLETED, COMPLETABLE, "completed", true);
    }

    /**
     * Marks a scheduled or confirmed appointment as a no-show. Patients cannot do this: the
     * clinical team decides whether the patient attended.
     */
    @Transactional
    public Appointment markNoShow(UUID appointmentId) {
        return transition(appointmentId, AppointmentStatus.NO_SHOW,
                AppointmentHistoryAction.NO_SHOW, NO_SHOWABLE, "marked as a no-show", true);
    }

    private Appointment transition(UUID appointmentId, AppointmentStatus target,
                                   AppointmentHistoryAction action, Set<AppointmentStatus> allowedFrom,
                                   String verb, boolean clinicalOnly) {
        Appointment appointment = appointmentRepository.findByAppointmentIdForUpdate(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with id: " + appointmentId));
        accessControl.assertCanAccess(appointment);
        if (clinicalOnly) {
            accessControl.assertClinicalAccess();
        }

        AppointmentStatus previous = appointment.getStatus();
        if (!allowedFrom.contains(previous)) {
            throw new ConflictException("Appointment cannot be " + verb
                    + " in status " + previous);
        }

        appointment.setStatus(target);
        appointment.setUpdatedBy(accessControl.currentUsername());
        appointment = appointmentRepository.saveAndFlush(appointment);
        recordHistory(appointment, action, previous, null);
        eventPublisher.publishUpdated(appointment);
        return appointment;
    }

    private void recordHistory(Appointment appointment, AppointmentHistoryAction action,
                               AppointmentStatus fromStatus, String details) {
        AppointmentHistory history = new AppointmentHistory();
        history.setAppointmentId(appointment.getAppointmentId());
        history.setAction(action);
        history.setFromStatus(fromStatus);
        history.setToStatus(appointment.getStatus());
        history.setActor(accessControl.currentUsername());
        history.setDetails(details);
        history.setOccurredAt(clinicClock.instant());
        history.setCreatedBy(accessControl.currentUsername());
        historyRepository.save(history);
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

    /**
     * Keeps the past-slot rule in the service as well as in the request DTO annotations, because
     * direct callers and reschedules must not be able to book backwards in the clinic's zone.
     */
    private void requireFuture(LocalDateTime startTime) {
        if (startTime.isBefore(clinicClock.now())) {
            throw new IllegalArgumentException("Appointment start time must be in the future");
        }
    }

    private boolean isMutable(AppointmentStatus status) {
        return status == AppointmentStatus.SCHEDULED || status == AppointmentStatus.CONFIRMED;
    }
}
