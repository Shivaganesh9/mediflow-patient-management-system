package com.mediflow.analytics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateMetricSnapshotRequest {

    @NotBlank
    private String category;

    @NotBlank
    private String metricName;

    @NotNull
    private Double value;

    @NotBlank
    private String unit;
}
