package com.mediflow.doctor.service;

import com.mediflow.common.exception.ConflictException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.doctor.dto.AvailabilityRequest;
import com.mediflow.doctor.dto.LeaveRequest;
import com.mediflow.doctor.dto.ScheduleValidationResponse;
import com.mediflow.doctor.entity.Doctor;
import com.mediflow.doctor.entity.DoctorAvailability;
import com.mediflow.doctor.entity.DoctorLeave;
import com.mediflow.doctor.repository.DoctorAvailabilityRepository;
import com.mediflow.doctor.repository.DoctorLeaveRepository;
import com.mediflow.doctor.repository.DoctorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorScheduleServiceTest {
    @Mock private DoctorService doctorService;
    @Mock private DoctorRepository doctorRepository;
    @Mock private DoctorAvailabilityRepository availabilityRepository;
    @Mock private DoctorLeaveRepository leaveRepository;
    @InjectMocks private DoctorScheduleService scheduleService;

    private final UUID doctorId = UUID.randomUUID();

    @Test
    void rejectsAvailabilityWithEndBeforeStart() {
        when(doctorService.getDoctor(doctorId)).thenReturn(new Doctor());
        AvailabilityRequest request = availability(DayOfWeek.MONDAY, LocalTime.NOON, LocalTime.of(11, 0));
        assertThrows(IllegalArgumentException.class, () -> scheduleService.addAvailability(doctorId, request));
    }

    @Test
    void rejectsOverlappingAvailabilityWindowOnTheSameDay() {
        when(doctorService.getDoctor(doctorId)).thenReturn(new Doctor());
        when(availabilityRepository.findByDoctorIdAndDayOfWeekAndActiveTrueAndDeletedAtIsNull(doctorId, DayOfWeek.MONDAY))
                .thenReturn(List.of(window(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0))));

        assertThrows(ConflictException.class, () -> scheduleService.addAvailability(doctorId,
                availability(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(13, 0))));
    }

    @Test
    void allowsBackToBackAvailabilityWindows() {
        when(doctorService.getDoctor(doctorId)).thenReturn(new Doctor());
        when(availabilityRepository.findByDoctorIdAndDayOfWeekAndActiveTrueAndDeletedAtIsNull(doctorId, DayOfWeek.MONDAY))
                .thenReturn(List.of(window(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0))));
        when(availabilityRepository.save(any(DoctorAvailability.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> scheduleService.addAvailability(doctorId,
                availability(DayOfWeek.MONDAY, LocalTime.of(12, 0), LocalTime.of(13, 0))));
    }

    @Test
    void updateAvailabilityKeepsTheWindowWhenActiveIsOmitted() {
        when(doctorService.getDoctor(doctorId)).thenReturn(new Doctor());
        DoctorAvailability existing = window(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        when(availabilityRepository.findByAvailabilityIdAndDoctorIdAndDeletedAtIsNull(
                existing.getAvailabilityId(), doctorId)).thenReturn(Optional.of(existing));
        when(availabilityRepository.findByDoctorIdAndDayOfWeekAndActiveTrueAndDeletedAtIsNull(doctorId, DayOfWeek.TUESDAY))
                .thenReturn(List.of(existing));
        when(availabilityRepository.save(any(DoctorAvailability.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DoctorAvailability updated = scheduleService.updateAvailability(doctorId,
                existing.getAvailabilityId(),
                availability(DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(13, 0)));

        assertTrue(updated.isActive());
        assertEquals(LocalTime.of(10, 0), updated.getStartTime());
    }

    @Test
    void deactivateAvailabilityIsIdempotent() {
        when(doctorService.getDoctor(doctorId)).thenReturn(new Doctor());
        DoctorAvailability existing = window(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));
        when(availabilityRepository.findByAvailabilityIdAndDoctorIdAndDeletedAtIsNull(
                existing.getAvailabilityId(), doctorId)).thenReturn(Optional.of(existing));
        when(availabilityRepository.save(any(DoctorAvailability.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DoctorAvailability result = scheduleService.deactivateAvailability(doctorId, existing.getAvailabilityId());
        assertFalse(result.isActive());
    }

    @Test
    void rejectsLeaveWithEndBeforeStart() {
        when(doctorService.getDoctor(doctorId)).thenReturn(new Doctor());
        LeaveRequest request = leave(LocalDate.of(2031, 3, 10), LocalDate.of(2031, 3, 9));
        assertThrows(IllegalArgumentException.class, () -> scheduleService.addLeave(doctorId, request));
    }

    @Test
    void rejectsOverlappingLeave() {
        when(doctorService.getDoctor(doctorId)).thenReturn(new Doctor());
        when(leaveRepository.existsByDoctorIdAndDeletedAtIsNullAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                doctorId, LocalDate.of(2031, 3, 12), LocalDate.of(2031, 3, 10))).thenReturn(true);

        assertThrows(ConflictException.class, () -> scheduleService.addLeave(doctorId,
                leave(LocalDate.of(2031, 3, 10), LocalDate.of(2031, 3, 12))));
    }

    @Test
    void cancelLeaveSoftDeletesTheRecord() {
        when(doctorService.getDoctor(doctorId)).thenReturn(new Doctor());
        DoctorLeave leave = new DoctorLeave();
        leave.setLeaveId(UUID.randomUUID());
        leave.setDoctorId(doctorId);
        leave.setStartDate(LocalDate.of(2031, 3, 10));
        leave.setEndDate(LocalDate.of(2031, 3, 12));
        when(leaveRepository.findByLeaveIdAndDoctorIdAndDeletedAtIsNull(leave.getLeaveId(), doctorId))
                .thenReturn(Optional.of(leave));
        when(leaveRepository.save(any(DoctorLeave.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DoctorLeave cancelled = scheduleService.cancelLeave(doctorId, leave.getLeaveId());
        assertTrue(cancelled.getDeletedAt() != null);
    }

    @Test
    void validateSlotAcceptsASlotInsideWorkingHours() {
        stubDoctorExists();
        when(leaveRepository.findByDoctorIdAndDeletedAtIsNull(doctorId)).thenReturn(List.of());
        when(availabilityRepository.findByDoctorIdAndDayOfWeekAndActiveTrueAndDeletedAtIsNull(doctorId, DayOfWeek.MONDAY))
                .thenReturn(List.of(window(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0))));

        ScheduleValidationResponse result = scheduleService.validateSlot(doctorId,
                LocalDateTime.of(2031, 3, 3, 10, 0), LocalDateTime.of(2031, 3, 3, 10, 30));

        assertTrue(result.valid());
    }

    @Test
    void validateSlotAcceptsExactBoundaries() {
        stubDoctorExists();
        when(leaveRepository.findByDoctorIdAndDeletedAtIsNull(doctorId)).thenReturn(List.of());
        when(availabilityRepository.findByDoctorIdAndDayOfWeekAndActiveTrueAndDeletedAtIsNull(doctorId, DayOfWeek.MONDAY))
                .thenReturn(List.of(window(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0))));

        assertTrue(scheduleService.validateSlot(doctorId,
                LocalDateTime.of(2031, 3, 3, 9, 0), LocalDateTime.of(2031, 3, 3, 17, 0)).valid());
    }

    @Test
    void validateSlotRejectsOutsideWorkingHours() {
        stubDoctorExists();
        when(leaveRepository.findByDoctorIdAndDeletedAtIsNull(doctorId)).thenReturn(List.of());
        when(availabilityRepository.findByDoctorIdAndDayOfWeekAndActiveTrueAndDeletedAtIsNull(doctorId, DayOfWeek.MONDAY))
                .thenReturn(List.of(window(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0))));

        ScheduleValidationResponse result = scheduleService.validateSlot(doctorId,
                LocalDateTime.of(2031, 3, 3, 16, 45), LocalDateTime.of(2031, 3, 3, 17, 15));

        assertFalse(result.valid());
        assertTrue(result.reason().contains("working hours"));
    }

    @Test
    void validateSlotRejectsADayWithoutWorkingHours() {
        stubDoctorExists();
        when(leaveRepository.findByDoctorIdAndDeletedAtIsNull(doctorId)).thenReturn(List.of());
        when(availabilityRepository.findByDoctorIdAndDayOfWeekAndActiveTrueAndDeletedAtIsNull(doctorId, DayOfWeek.SUNDAY))
                .thenReturn(List.of());

        ScheduleValidationResponse result = scheduleService.validateSlot(doctorId,
                LocalDateTime.of(2031, 3, 9, 10, 0), LocalDateTime.of(2031, 3, 9, 10, 30));

        assertFalse(result.valid());
        assertTrue(result.reason().contains("no working hours"));
    }

    @Test
    void validateSlotRejectsDatesCoveredByLeave() {
        stubDoctorExists();
        DoctorLeave leave = new DoctorLeave();
        leave.setStartDate(LocalDate.of(2031, 3, 3));
        leave.setEndDate(LocalDate.of(2031, 3, 4));
        when(leaveRepository.findByDoctorIdAndDeletedAtIsNull(doctorId)).thenReturn(List.of(leave));

        ScheduleValidationResponse result = scheduleService.validateSlot(doctorId,
                LocalDateTime.of(2031, 3, 3, 10, 0), LocalDateTime.of(2031, 3, 3, 10, 30));

        assertFalse(result.valid());
        assertTrue(result.reason().contains("on leave"));
    }

    @Test
    void validateSlotRejectsMultiDayAndInvertedRanges() {
        stubDoctorExists();

        assertFalse(scheduleService.validateSlot(doctorId,
                LocalDateTime.of(2031, 3, 3, 23, 30), LocalDateTime.of(2031, 3, 4, 0, 30)).valid());
        assertFalse(scheduleService.validateSlot(doctorId,
                LocalDateTime.of(2031, 3, 3, 10, 30), LocalDateTime.of(2031, 3, 3, 10, 0)).valid());
    }

    @Test
    void validateSlotRejectsUnknownDoctor() {
        when(doctorRepository.existsByDoctorId(doctorId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> scheduleService.validateSlot(doctorId,
                LocalDateTime.of(2031, 3, 3, 10, 0), LocalDateTime.of(2031, 3, 3, 10, 30)));
    }

    private void stubDoctorExists() {
        when(doctorRepository.existsByDoctorId(doctorId)).thenReturn(true);
    }

    private AvailabilityRequest availability(DayOfWeek day, LocalTime start, LocalTime end) {
        AvailabilityRequest request = new AvailabilityRequest();
        request.setDayOfWeek(day);
        request.setStartTime(start);
        request.setEndTime(end);
        return request;
    }

    private LeaveRequest leave(LocalDate start, LocalDate end) {
        LeaveRequest request = new LeaveRequest();
        request.setStartDate(start);
        request.setEndDate(end);
        return request;
    }

    private DoctorAvailability window(DayOfWeek day, LocalTime start, LocalTime end) {
        DoctorAvailability availability = new DoctorAvailability();
        availability.setAvailabilityId(UUID.randomUUID());
        availability.setDoctorId(doctorId);
        availability.setDayOfWeek(day);
        availability.setStartTime(start);
        availability.setEndTime(end);
        availability.setActive(true);
        return availability;
    }
}
