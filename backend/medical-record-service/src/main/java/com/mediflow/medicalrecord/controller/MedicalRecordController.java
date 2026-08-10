package com.mediflow.medicalrecord.controller;

import com.mediflow.common.response.ApiResponse;
import com.mediflow.medicalrecord.dto.CreateMedicalRecordRequest;
import com.mediflow.medicalrecord.entity.MedicalRecord;
import com.mediflow.medicalrecord.service.MedicalRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/medical-records")
@RequiredArgsConstructor
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MedicalRecord> createMedicalRecord(
            @Valid @RequestBody CreateMedicalRecordRequest request) {
        return ApiResponse.success(medicalRecordService.createMedicalRecord(request), "Medical record created successfully");
    }

    @GetMapping("/{recordId}")
    public ApiResponse<MedicalRecord> getMedicalRecord(@PathVariable UUID recordId) {
        return ApiResponse.success(medicalRecordService.getMedicalRecord(recordId), "Medical record retrieved successfully");
    }

    @PutMapping("/{recordId}")
    public ApiResponse<MedicalRecord> updateMedicalRecord(
            @PathVariable UUID recordId,
            @Valid @RequestBody CreateMedicalRecordRequest request) {
        return ApiResponse.success(medicalRecordService.updateMedicalRecord(recordId, request), "Medical record updated successfully");
    }
}
