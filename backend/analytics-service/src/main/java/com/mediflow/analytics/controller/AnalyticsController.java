package com.mediflow.analytics.controller;

import com.mediflow.analytics.dto.AppointmentsResponse;
import com.mediflow.analytics.dto.DashboardSummaryResponse;
import com.mediflow.analytics.dto.DoctorStatResponse;
import com.mediflow.analytics.dto.PatientsResponse;
import com.mediflow.analytics.dto.RevenueResponse;
import com.mediflow.analytics.service.AnalyticsService;
import com.mediflow.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard/summary")
    public ApiResponse<DashboardSummaryResponse> getDashboardSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(analyticsService.getDashboardSummary(from, to),
                "Dashboard summary retrieved successfully");
    }

    @GetMapping("/appointments")
    public ApiResponse<AppointmentsResponse> getAppointments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(analyticsService.getAppointments(from, to),
                "Appointment statistics retrieved successfully");
    }

    @GetMapping("/revenue")
    public ApiResponse<RevenueResponse> getRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(analyticsService.getRevenue(from, to),
                "Revenue statistics retrieved successfully");
    }

    @GetMapping("/doctors")
    public ApiResponse<List<DoctorStatResponse>> getDoctors(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(analyticsService.getDoctorStats(from, to),
                "Doctor statistics retrieved successfully");
    }

    @GetMapping("/patients")
    public ApiResponse<PatientsResponse> getPatients(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(analyticsService.getPatients(from, to),
                "Patient statistics retrieved successfully");
    }
}