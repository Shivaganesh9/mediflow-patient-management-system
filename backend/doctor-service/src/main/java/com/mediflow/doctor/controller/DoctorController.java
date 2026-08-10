package com.mediflow.doctor.controller;

import com.mediflow.common.response.ApiResponse;
import com.mediflow.doctor.dto.CreateDoctorRequest;
import com.mediflow.doctor.dto.DoctorResponse;
import com.mediflow.doctor.mapper.DoctorMapper;
import com.mediflow.doctor.service.DoctorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;
    private final DoctorMapper doctorMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DoctorResponse> createDoctor(@Valid @RequestBody CreateDoctorRequest request) {
        return ApiResponse.success(doctorMapper.toResponse(doctorService.createDoctor(request)), "Doctor created successfully");
    }

    @GetMapping
    public ApiResponse<Page<DoctorResponse>> getAllDoctors(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String department,
            @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {
        return ApiResponse.success(doctorService.searchDoctors(search, department, pageable), "Doctors retrieved successfully");
    }

    @GetMapping("/{doctorId}")
    public ApiResponse<DoctorResponse> getDoctor(@PathVariable UUID doctorId) {
        return ApiResponse.success(doctorMapper.toResponse(doctorService.getDoctor(doctorId)), "Doctor retrieved successfully");
    }

    @PutMapping("/{doctorId}")
    public ApiResponse<DoctorResponse> updateDoctor(@PathVariable UUID doctorId, @Valid @RequestBody CreateDoctorRequest request) {
        return ApiResponse.success(doctorMapper.toResponse(doctorService.updateDoctor(doctorId, request)), "Doctor updated successfully");
    }
}
