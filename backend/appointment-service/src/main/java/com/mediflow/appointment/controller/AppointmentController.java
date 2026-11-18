package com.mediflow.appointment.controller;

import com.mediflow.appointment.dto.AppointmentHistoryResponse;
import com.mediflow.appointment.dto.AppointmentResponse;
import com.mediflow.appointment.dto.CreateAppointmentRequest;
import com.mediflow.appointment.dto.UpdateAppointmentRequest;
import com.mediflow.appointment.entity.AppointmentStatus;
import com.mediflow.appointment.mapper.AppointmentMapper;
import com.mediflow.appointment.service.AppointmentService;
import com.mediflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentMapper appointmentMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AppointmentResponse> createAppointment(@Valid @RequestBody CreateAppointmentRequest request) {
        return ApiResponse.success(
                appointmentMapper.toResponse(appointmentService.createAppointment(request)),
                "Appointment created successfully");
    }

    @GetMapping
    public ApiResponse<Page<AppointmentResponse>> getAppointments(
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID doctorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toTime,
            @PageableDefault(size = 20, sort = "startTime") Pageable pageable) {
        Page<AppointmentResponse> appointments = appointmentService
                .searchAppointments(status, patientId, doctorId, fromTime, toTime, pageable)
                .map(appointmentMapper::toResponse);
        return ApiResponse.success(appointments, "Appointments retrieved successfully");
    }

    @GetMapping("/{appointmentId}")
    public ApiResponse<AppointmentResponse> getAppointment(@PathVariable UUID appointmentId) {
        return ApiResponse.success(
                appointmentMapper.toResponse(appointmentService.getAppointment(appointmentId)),
                "Appointment retrieved successfully");
    }

    @GetMapping("/{appointmentId}/history")
    public ApiResponse<List<AppointmentHistoryResponse>> getAppointmentHistory(
            @PathVariable UUID appointmentId) {
        List<AppointmentHistoryResponse> history = appointmentService
                .getAppointmentHistory(appointmentId)
                .stream()
                .map(appointmentMapper::toHistoryResponse)
                .toList();
        return ApiResponse.success(history, "Appointment history retrieved successfully");
    }

    @PutMapping("/{appointmentId}")
    public ApiResponse<AppointmentResponse> updateAppointment(@PathVariable UUID appointmentId,
                                                              @Valid @RequestBody UpdateAppointmentRequest request) {
        return ApiResponse.success(
                appointmentMapper.toResponse(appointmentService.updateAppointment(appointmentId, request)),
                "Appointment updated successfully");
    }

    @PatchMapping("/{appointmentId}/confirm")
    public ApiResponse<AppointmentResponse> confirmAppointment(@PathVariable UUID appointmentId) {
        return ApiResponse.success(
                appointmentMapper.toResponse(appointmentService.confirmAppointment(appointmentId)),
                "Appointment confirmed successfully");
    }

    @PatchMapping("/{appointmentId}/start")
    public ApiResponse<AppointmentResponse> startAppointment(@PathVariable UUID appointmentId) {
        return ApiResponse.success(
                appointmentMapper.toResponse(appointmentService.startAppointment(appointmentId)),
                "Appointment started successfully");
    }

    @PatchMapping("/{appointmentId}/complete")
    public ApiResponse<AppointmentResponse> completeAppointment(@PathVariable UUID appointmentId) {
        return ApiResponse.success(
                appointmentMapper.toResponse(appointmentService.completeAppointment(appointmentId)),
                "Appointment completed successfully");
    }

    @PatchMapping("/{appointmentId}/no-show")
    public ApiResponse<AppointmentResponse> markNoShow(@PathVariable UUID appointmentId) {
        return ApiResponse.success(
                appointmentMapper.toResponse(appointmentService.markNoShow(appointmentId)),
                "Appointment marked as a no-show");
    }

    @PatchMapping("/{appointmentId}/cancel")
    public ApiResponse<AppointmentResponse> cancelAppointment(@PathVariable UUID appointmentId) {
        return ApiResponse.success(
                appointmentMapper.toResponse(appointmentService.cancelAppointment(appointmentId)),
                "Appointment cancelled successfully");
    }
}
