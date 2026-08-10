package com.mediflow.appointment.controller;

import com.mediflow.appointment.dto.CreateAppointmentRequest;
import com.mediflow.appointment.entity.Appointment;
import com.mediflow.appointment.service.AppointmentService;
import com.mediflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Appointment> createAppointment(@Valid @RequestBody CreateAppointmentRequest request) {
        return ApiResponse.success(appointmentService.createAppointment(request), "Appointment created successfully");
    }

    @GetMapping
    public ApiResponse<List<Appointment>> getAllAppointments() {
        return ApiResponse.success(appointmentService.getAllAppointments(), "Appointments retrieved successfully");
    }

    @GetMapping("/{appointmentId}")
    public ApiResponse<Appointment> getAppointment(@PathVariable UUID appointmentId) {
        return ApiResponse.success(appointmentService.getAppointment(appointmentId), "Appointment retrieved successfully");
    }

    @PatchMapping("/{appointmentId}/cancel")
    public ApiResponse<Appointment> cancelAppointment(@PathVariable UUID appointmentId) {
        return ApiResponse.success(appointmentService.cancelAppointment(appointmentId), "Appointment cancelled successfully");
    }
}
