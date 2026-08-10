package com.mediflow.doctor.service;

import com.mediflow.doctor.dto.AvailabilityRequest;
import com.mediflow.doctor.entity.Doctor;
import com.mediflow.doctor.repository.DoctorAvailabilityRepository;
import com.mediflow.doctor.repository.DoctorLeaveRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorScheduleServiceTest {
    @Mock private DoctorService doctorService;
    @Mock private DoctorAvailabilityRepository availabilityRepository;
    @Mock private DoctorLeaveRepository leaveRepository;
    @InjectMocks private DoctorScheduleService scheduleService;

    @Test
    void rejectsAvailabilityWithEndBeforeStart() {
        UUID doctorId = UUID.randomUUID();
        when(doctorService.getDoctor(doctorId)).thenReturn(new Doctor());
        AvailabilityRequest request = new AvailabilityRequest();
        request.setDayOfWeek(DayOfWeek.MONDAY);
        request.setStartTime(LocalTime.NOON);
        request.setEndTime(LocalTime.of(11, 0));
        assertThrows(IllegalArgumentException.class, () -> scheduleService.addAvailability(doctorId, request));
    }
}
