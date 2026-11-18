package com.mediflow.doctor.service;

import com.mediflow.common.exception.ConflictException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.doctor.dto.AvailabilityRequest;
import com.mediflow.doctor.dto.LeaveRequest;
import com.mediflow.doctor.dto.ScheduleValidationResponse;
import com.mediflow.doctor.entity.DoctorAvailability;
import com.mediflow.doctor.entity.DoctorLeave;
import com.mediflow.doctor.repository.DoctorAvailabilityRepository;
import com.mediflow.doctor.repository.DoctorLeaveRepository;
import com.mediflow.doctor.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Doctor weekly availability and leave management.
 *
 * <p>Every mutation is scoped to the doctor in the path: admins may manage any doctor's schedule,
 * doctors may manage only their own (enforced by {@link DoctorService#getDoctor(UUID)}), and other
 * roles are rejected by the HTTP security rules. Availability windows cannot overlap each other on
 * the same day and active leaves cannot overlap each other; both are conflicts (409) rather than
 * invalid input (400).</p>
 */
@Service
@RequiredArgsConstructor
public class DoctorScheduleService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final DoctorService doctorService;
    private final DoctorRepository doctorRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final DoctorLeaveRepository leaveRepository;

    @Transactional
    public DoctorAvailability addAvailability(UUID doctorId, AvailabilityRequest request) {
        doctorService.getDoctor(doctorId);
        validateWindow(request.getStartTime(), request.getEndTime());
        assertNoAvailabilityOverlap(doctorId, request.getDayOfWeek(), request.getStartTime(),
                request.getEndTime(), null);

        DoctorAvailability availability = new DoctorAvailability();
        availability.setDoctorId(doctorId);
        availability.setDayOfWeek(request.getDayOfWeek());
        availability.setStartTime(request.getStartTime());
        availability.setEndTime(request.getEndTime());
        availability.setActive(request.getActive() == null || request.getActive());
        return availabilityRepository.save(availability);
    }

    @Transactional
    public DoctorAvailability updateAvailability(UUID doctorId, UUID availabilityId,
                                                 AvailabilityRequest request) {
        doctorService.getDoctor(doctorId);
        DoctorAvailability availability = availabilityRepository
                .findByAvailabilityIdAndDoctorIdAndDeletedAtIsNull(availabilityId, doctorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Availability not found with id: " + availabilityId));

        validateWindow(request.getStartTime(), request.getEndTime());
        boolean active = request.getActive() == null ? availability.isActive() : request.getActive();
        if (active) {
            assertNoAvailabilityOverlap(doctorId, request.getDayOfWeek(), request.getStartTime(),
                    request.getEndTime(), availabilityId);
        }

        availability.setDayOfWeek(request.getDayOfWeek());
        availability.setStartTime(request.getStartTime());
        availability.setEndTime(request.getEndTime());
        availability.setActive(active);
        return availabilityRepository.save(availability);
    }

    /**
     * Retires a window without deleting its history. Idempotent: an already inactive window is
     * returned unchanged. Existing appointments remain valid because the window is only used for
     * new bookings.
     */
    @Transactional
    public DoctorAvailability deactivateAvailability(UUID doctorId, UUID availabilityId) {
        doctorService.getDoctor(doctorId);
        DoctorAvailability availability = availabilityRepository
                .findByAvailabilityIdAndDoctorIdAndDeletedAtIsNull(availabilityId, doctorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Availability not found with id: " + availabilityId));
        availability.setActive(false);
        return availabilityRepository.save(availability);
    }

    @Transactional(readOnly = true)
    public List<DoctorAvailability> getAvailability(UUID doctorId) {
        doctorService.getDoctor(doctorId);
        return availabilityRepository
                .findByDoctorIdAndActiveTrueAndDeletedAtIsNullOrderByDayOfWeekAscStartTimeAsc(doctorId);
    }

    @Transactional
    public DoctorLeave addLeave(UUID doctorId, LeaveRequest request) {
        doctorService.getDoctor(doctorId);
        validateLeaveRange(request.getStartDate(), request.getEndDate());
        assertNoLeaveOverlap(doctorId, request.getStartDate(), request.getEndDate(), null);

        DoctorLeave leave = new DoctorLeave();
        leave.setDoctorId(doctorId);
        leave.setStartDate(request.getStartDate());
        leave.setEndDate(request.getEndDate());
        leave.setReason(request.getReason());
        return leaveRepository.save(leave);
    }

    @Transactional
    public DoctorLeave updateLeave(UUID doctorId, UUID leaveId, LeaveRequest request) {
        doctorService.getDoctor(doctorId);
        DoctorLeave leave = leaveRepository
                .findByLeaveIdAndDoctorIdAndDeletedAtIsNull(leaveId, doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave not found with id: " + leaveId));

        validateLeaveRange(request.getStartDate(), request.getEndDate());
        assertNoLeaveOverlap(doctorId, request.getStartDate(), request.getEndDate(), leaveId);

        leave.setStartDate(request.getStartDate());
        leave.setEndDate(request.getEndDate());
        leave.setReason(request.getReason());
        return leaveRepository.save(leave);
    }

    /**
     * Cancels a leave by soft-deleting it, so the record remains auditable and can be restored by
     * an operator if it was cancelled by mistake.
     */
    @Transactional
    public DoctorLeave cancelLeave(UUID doctorId, UUID leaveId) {
        doctorService.getDoctor(doctorId);
        DoctorLeave leave = leaveRepository
                .findByLeaveIdAndDoctorIdAndDeletedAtIsNull(leaveId, doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave not found with id: " + leaveId));
        leave.setDeletedAt(Instant.now());
        return leaveRepository.save(leave);
    }

    @Transactional(readOnly = true)
    public List<DoctorLeave> getLeaves(UUID doctorId) {
        doctorService.getDoctor(doctorId);
        return leaveRepository.findByDoctorIdAndDeletedAtIsNullOrderByStartDateAsc(doctorId);
    }

    /**
     * Internal contract used by appointment-service before committing a booking or reschedule.
     *
     * <p>Deliberately does not consult the caller's profile access: a patient booking an
     * appointment must be able to verify the doctor's schedule even though they cannot read the
     * doctor's profile endpoints. The endpoint is not exposed through the API gateway.</p>
     */
    @Transactional(readOnly = true)
    public ScheduleValidationResponse validateSlot(UUID doctorId, LocalDateTime startTime,
                                                   LocalDateTime endTime) {
        if (!doctorRepository.existsByDoctorId(doctorId)) {
            throw new ResourceNotFoundException("Doctor not found with id: " + doctorId);
        }
        if (startTime == null || endTime == null) {
            return ScheduleValidationResponse.rejected("Appointment start and end times are required");
        }
        if (!endTime.isAfter(startTime)) {
            return ScheduleValidationResponse.rejected("Appointment end time must be after start time");
        }
        if (!startTime.toLocalDate().equals(endTime.toLocalDate())) {
            return ScheduleValidationResponse.rejected("An appointment must start and end on the same day");
        }

        LocalDate date = startTime.toLocalDate();
        boolean onLeave = leaveRepository
                .findByDoctorIdAndDeletedAtIsNull(doctorId)
                .stream()
                .anyMatch(leave -> !date.isBefore(leave.getStartDate()) && !date.isAfter(leave.getEndDate()));
        if (onLeave) {
            return ScheduleValidationResponse.rejected(
                    "The doctor is on leave on " + date + ". Please choose another date.");
        }

        List<DoctorAvailability> windows = availabilityRepository
                .findByDoctorIdAndDayOfWeekAndActiveTrueAndDeletedAtIsNull(doctorId,
                        startTime.getDayOfWeek());
        if (windows.isEmpty()) {
            return ScheduleValidationResponse.rejected(
                    "The doctor has no working hours configured for " + startTime.getDayOfWeek());
        }

        LocalTime requestedStart = startTime.toLocalTime();
        LocalTime requestedEnd = endTime.toLocalTime();
        boolean fits = windows.stream().anyMatch(window ->
                !requestedStart.isBefore(window.getStartTime())
                        && !requestedEnd.isAfter(window.getEndTime()));
        if (!fits) {
            return ScheduleValidationResponse.rejected(
                    "The requested slot is outside the doctor's working hours ("
                            + describeWindows(windows) + ")");
        }
        return ScheduleValidationResponse.ok();
    }

    private void validateWindow(LocalTime start, LocalTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Availability end time must be after start time");
        }
    }

    private void validateLeaveRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Leave end date must not be before start date");
        }
    }

    private void assertNoAvailabilityOverlap(UUID doctorId, java.time.DayOfWeek dayOfWeek,
                                             LocalTime start, LocalTime end, UUID excludeId) {
        boolean overlaps = availabilityRepository
                .findByDoctorIdAndDayOfWeekAndActiveTrueAndDeletedAtIsNull(doctorId, dayOfWeek)
                .stream()
                .filter(window -> excludeId == null || !excludeId.equals(window.getAvailabilityId()))
                .anyMatch(window -> start.isBefore(window.getEndTime()) && end.isAfter(window.getStartTime()));
        if (overlaps) {
            throw new ConflictException("Availability overlaps an existing working window on " + dayOfWeek);
        }
    }

    private void assertNoLeaveOverlap(UUID doctorId, LocalDate startDate, LocalDate endDate, UUID excludeId) {
        boolean overlaps = excludeId == null
                ? leaveRepository.existsByDoctorIdAndDeletedAtIsNullAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        doctorId, endDate, startDate)
                : leaveRepository.existsByDoctorIdAndDeletedAtIsNullAndLeaveIdNotAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        doctorId, excludeId, endDate, startDate);
        if (overlaps) {
            throw new ConflictException("Leave overlaps an existing leave period for this doctor");
        }
    }

    private String describeWindows(List<DoctorAvailability> windows) {
        return windows.stream()
                .map(window -> TIME.format(window.getStartTime()) + "-" + TIME.format(window.getEndTime()))
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("no windows");
    }
}
