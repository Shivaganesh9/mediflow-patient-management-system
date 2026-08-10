package com.mediflow.analytics.controller;

import com.mediflow.analytics.dto.CreateMetricSnapshotRequest;
import com.mediflow.analytics.entity.MetricSnapshot;
import com.mediflow.analytics.service.AnalyticsService;
import com.mediflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @PostMapping("/snapshots")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MetricSnapshot> createSnapshot(@Valid @RequestBody CreateMetricSnapshotRequest request) {
        return ApiResponse.success(analyticsService.createSnapshot(request), "Metric snapshot created successfully");
    }

    @GetMapping("/snapshots")
    public ApiResponse<List<MetricSnapshot>> getAllSnapshots() {
        return ApiResponse.success(analyticsService.getAllSnapshots(), "Metric snapshots retrieved successfully");
    }

    @GetMapping("/snapshots/{snapshotId}")
    public ApiResponse<MetricSnapshot> getSnapshot(@PathVariable UUID snapshotId) {
        return ApiResponse.success(analyticsService.getSnapshot(snapshotId), "Metric snapshot retrieved successfully");
    }
}
