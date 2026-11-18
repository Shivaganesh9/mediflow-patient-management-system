package com.mediflow.doctor.controller;

import com.mediflow.common.response.ApiResponse;
import com.mediflow.doctor.dto.ScheduleValidationResponse;
import com.mediflow.doctor.service.DoctorScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service-to-service schedule contract. Not routed through the API gateway: only appointment-service
 * calls it, forwarding the signed caller token.
 */
@RestController
@RequestMapping("/internal/doctors/{doctorId}/schedule")
@RequiredArgsConstructor
public class InternalDoctorScheduleController {

    private final DoctorScheduleService scheduleService;

    @GetMapping("/validation")
    public ApiResponse<ScheduleValidationResponse> validate(
            @PathVariable UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ApiResponse.success(
                scheduleService.validateSlot(doctorId, startTime, endTime),
                "Schedule validation completed");
    }
}
