package com.mediflow.patient.controller;

import com.mediflow.common.response.ApiResponse;
import com.mediflow.patient.dto.CreatePatientRequest;
import com.mediflow.patient.dto.PatientResponse;
import com.mediflow.patient.mapper.PatientMapper;
import com.mediflow.patient.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;
    private final PatientMapper patientMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PatientResponse> createPatient(@Valid @RequestBody CreatePatientRequest request) {
        return ApiResponse.success(patientMapper.toResponse(patientService.createPatient(request)), "Patient created successfully");
    }

    @GetMapping
    public ApiResponse<Page<PatientResponse>> getAllPatients(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {
        return ApiResponse.success(patientService.searchPatients(search, pageable), "Patients retrieved successfully");
    }

    @GetMapping("/{patientId}")
    public ApiResponse<PatientResponse> getPatient(@PathVariable UUID patientId) {
        return ApiResponse.success(patientMapper.toResponse(patientService.getPatient(patientId)), "Patient retrieved successfully");
    }

    @PutMapping("/{patientId}")
    public ApiResponse<PatientResponse> updatePatient(@PathVariable UUID patientId, @Valid @RequestBody CreatePatientRequest request) {
        return ApiResponse.success(patientMapper.toResponse(patientService.updatePatient(patientId, request)), "Patient updated successfully");
    }

    @DeleteMapping("/{patientId}")
    public ResponseEntity<ApiResponse<Void>> deletePatient(@PathVariable UUID patientId) {
        patientService.deletePatient(patientId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.success(null, "Patient deleted successfully"));
    }
}
