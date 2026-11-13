package com.mediflow.auth.controller;

import com.mediflow.auth.dto.PasswordResetConfirmRequest;
import com.mediflow.auth.dto.PasswordResetRequest;
import com.mediflow.auth.dto.PasswordResetResponse;
import com.mediflow.auth.service.PasswordResetService;
import com.mediflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<PasswordResetResponse>> request(@Valid @RequestBody PasswordResetRequest request) {
        return ResponseEntity.ok(ApiResponse.success(passwordResetService.requestReset(request),
                "Password reset request accepted"));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<Void>> confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request);
        return ResponseEntity.ok(ApiResponse.success(null,
                "Password reset successfully. Please sign in with the new password."));
    }
}
