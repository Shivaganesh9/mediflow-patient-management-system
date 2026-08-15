package com.mediflow.analytics.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class DoctorStatResponse {
    UUID doctorId;
    long appointments;
    long completed;
}