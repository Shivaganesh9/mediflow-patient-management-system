package com.mediflow.doctor.service;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.doctor.dto.AvailabilityRequest;
import com.mediflow.doctor.dto.LeaveRequest;
import com.mediflow.doctor.entity.DoctorAvailability;
import com.mediflow.doctor.entity.DoctorLeave;
import com.mediflow.doctor.repository.DoctorAvailabilityRepository;
import com.mediflow.doctor.repository.DoctorLeaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DoctorScheduleService {
    private final DoctorService doctorService;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final DoctorLeaveRepository leaveRepository;

    @Transactional
    public DoctorAvailability addAvailability(UUID doctorId, AvailabilityRequest request) {
        doctorService.getDoctor(doctorId);
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new IllegalArgumentException("Availability end time must be after start time");
        }
        DoctorAvailability availability = new DoctorAvailability();
        availability.setDoctorId(doctorId);
        availability.setDayOfWeek(request.getDayOfWeek());
        availability.setStartTime(request.getStartTime());
        availability.setEndTime(request.getEndTime());
        return availabilityRepository.save(availability);
    }

    @Transactional(readOnly = true)
    public List<DoctorAvailability> getAvailability(UUID doctorId) {
        doctorService.getDoctor(doctorId);
        return availabilityRepository.findByDoctorIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(doctorId);
    }

    @Transactional
    public DoctorLeave addLeave(UUID doctorId, LeaveRequest request) {
        doctorService.getDoctor(doctorId);
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("Leave end date must not be before start date");
        }
        DoctorLeave leave = new DoctorLeave();
        leave.setDoctorId(doctorId);
        leave.setStartDate(request.getStartDate());
        leave.setEndDate(request.getEndDate());
        leave.setReason(request.getReason());
        return leaveRepository.save(leave);
    }

    @Transactional(readOnly = true)
    public List<DoctorLeave> getLeaves(UUID doctorId) {
        doctorService.getDoctor(doctorId);
        return leaveRepository.findByDoctorIdOrderByStartDateAsc(doctorId);
    }
}
