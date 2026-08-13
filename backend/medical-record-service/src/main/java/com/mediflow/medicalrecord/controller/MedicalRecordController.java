package com.mediflow.medicalrecord.controller;

import com.mediflow.common.response.ApiResponse;
import com.mediflow.medicalrecord.dto.CreateMedicalRecordRequest;
import com.mediflow.medicalrecord.dto.MedicalRecordResponse;
import com.mediflow.medicalrecord.dto.UpdateMedicalRecordRequest;
import com.mediflow.medicalrecord.entity.MedicalRecordType;
import com.mediflow.medicalrecord.mapper.MedicalRecordMapper;
import com.mediflow.medicalrecord.service.MedicalRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/medical-records")
@RequiredArgsConstructor
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;
    private final MedicalRecordMapper medicalRecordMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MedicalRecordResponse> createMedicalRecord(
            @Valid @RequestBody CreateMedicalRecordRequest request) {
        return ApiResponse.success(
                medicalRecordMapper.toResponse(medicalRecordService.createMedicalRecord(request)),
                "Medical record created successfully");
    }

    @GetMapping
    public ApiResponse<Page<MedicalRecordResponse>> searchMedicalRecords(
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID doctorId,
            @RequestParam(required = false) MedicalRecordType recordType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 20, sort = "encounterDate") Pageable pageable) {
        Page<MedicalRecordResponse> records = medicalRecordService
                .searchMedicalRecords(patientId, doctorId, recordType, fromDate, toDate, pageable)
                .map(medicalRecordMapper::toResponse);
        return ApiResponse.success(records, "Medical records retrieved successfully");
    }

    @GetMapping("/{recordId}")
    public ApiResponse<MedicalRecordResponse> getMedicalRecord(@PathVariable UUID recordId) {
        return ApiResponse.success(
                medicalRecordMapper.toResponse(medicalRecordService.getMedicalRecord(recordId)),
                "Medical record retrieved successfully");
    }

    @PutMapping("/{recordId}")
    public ApiResponse<MedicalRecordResponse> updateMedicalRecord(
            @PathVariable UUID recordId,
            @Valid @RequestBody UpdateMedicalRecordRequest request) {
        return ApiResponse.success(
                medicalRecordMapper.toResponse(medicalRecordService.updateMedicalRecord(recordId, request)),
                "Medical record updated successfully");
    }
}
