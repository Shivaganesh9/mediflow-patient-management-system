package com.mediflow.doctor.controller;

import com.mediflow.common.response.ApiResponse;
import com.mediflow.doctor.dto.AvailabilityRequest;
import com.mediflow.doctor.dto.LeaveRequest;
import com.mediflow.doctor.entity.DoctorAvailability;
import com.mediflow.doctor.entity.DoctorLeave;
import com.mediflow.doctor.service.DoctorScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/doctors/{doctorId}")
@RequiredArgsConstructor
public class DoctorScheduleController {
    private final DoctorScheduleService scheduleService;

    @PostMapping("/availability")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DoctorAvailability> addAvailability(@PathVariable UUID doctorId,
                                                             @Valid @RequestBody AvailabilityRequest request) {
        return ApiResponse.success(scheduleService.addAvailability(doctorId, request), "Availability added successfully");
    }

    @GetMapping("/availability")
    public ApiResponse<List<DoctorAvailability>> getAvailability(@PathVariable UUID doctorId) {
        return ApiResponse.success(scheduleService.getAvailability(doctorId), "Availability retrieved successfully");
    }

    @PutMapping("/availability/{availabilityId}")
    public ApiResponse<DoctorAvailability> updateAvailability(@PathVariable UUID doctorId,
                                                               @PathVariable UUID availabilityId,
                                                               @Valid @RequestBody AvailabilityRequest request) {
        return ApiResponse.success(
                scheduleService.updateAvailability(doctorId, availabilityId, request),
                "Availability updated successfully");
    }

    /**
     * Retires a working window. Existing appointments are not affected; the window simply stops
     * accepting new bookings.
     */
    @DeleteMapping("/availability/{availabilityId}")
    public ApiResponse<DoctorAvailability> deactivateAvailability(@PathVariable UUID doctorId,
                                                                  @PathVariable UUID availabilityId) {
        return ApiResponse.success(
                scheduleService.deactivateAvailability(doctorId, availabilityId),
                "Availability deactivated successfully");
    }

    @PostMapping("/leaves")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DoctorLeave> addLeave(@PathVariable UUID doctorId,
                                              @Valid @RequestBody LeaveRequest request) {
        return ApiResponse.success(scheduleService.addLeave(doctorId, request), "Leave added successfully");
    }

    @GetMapping("/leaves")
    public ApiResponse<List<DoctorLeave>> getLeaves(@PathVariable UUID doctorId) {
        return ApiResponse.success(scheduleService.getLeaves(doctorId), "Leaves retrieved successfully");
    }

    @PutMapping("/leaves/{leaveId}")
    public ApiResponse<DoctorLeave> updateLeave(@PathVariable UUID doctorId,
                                                @PathVariable UUID leaveId,
                                                @Valid @RequestBody LeaveRequest request) {
        return ApiResponse.success(
                scheduleService.updateLeave(doctorId, leaveId, request),
                "Leave updated successfully");
    }

    @DeleteMapping("/leaves/{leaveId}")
    public ApiResponse<DoctorLeave> cancelLeave(@PathVariable UUID doctorId,
                                                @PathVariable UUID leaveId) {
        return ApiResponse.success(
                scheduleService.cancelLeave(doctorId, leaveId),
                "Leave cancelled successfully");
    }
}
