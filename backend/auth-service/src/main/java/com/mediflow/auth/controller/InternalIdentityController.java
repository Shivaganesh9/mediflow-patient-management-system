package com.mediflow.auth.controller;

import com.mediflow.auth.dto.IdentityResponse;
import com.mediflow.auth.service.AuthService;
import com.mediflow.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal service-to-service identity lookup used by patient-service and doctor-service
 * to validate that a profile references a real auth identity with the expected role.
 *
 * <p>This endpoint is intentionally <strong>not</strong> part of the public API: it lives
 * outside {@code /api/v1}, is hidden from the OpenAPI document, has no gateway route, and
 * requires a bearer token whose caller holds a staff/doctor role (see {@code SecurityConfig}).
 * It returns only id, username, status and roles - never credentials or contact data.</p>
 */
@RestController
@RequestMapping("/internal/identities")
@RequiredArgsConstructor
@Hidden
public class InternalIdentityController {

    private final AuthService authService;

    @GetMapping("/{userId}")
    public ApiResponse<IdentityResponse> getIdentity(@PathVariable UUID userId) {
        return ApiResponse.success(authService.getIdentity(userId), "Identity retrieved successfully");
    }
}
