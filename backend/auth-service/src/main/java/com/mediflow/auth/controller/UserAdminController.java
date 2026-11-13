package com.mediflow.auth.controller;

import com.mediflow.auth.dto.UpdateAccountStatusRequest;
import com.mediflow.auth.dto.UpdateRolesRequest;
import com.mediflow.auth.dto.UserProfileResponse;
import com.mediflow.auth.entity.User;
import com.mediflow.auth.repository.UserRepository;
import com.mediflow.auth.service.AccountLifecycleService;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Administrative identity lifecycle API. Restricted to ADMIN by {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/auth/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final AccountLifecycleService accountLifecycleService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(accountLifecycleService.listUsers(),
                "Users retrieved successfully"));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> get(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(accountLifecycleService.getUser(userId),
                "User retrieved successfully"));
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateAccountStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                accountLifecycleService.updateStatus(actorId(userDetails), userId, request),
                "Account status updated successfully"));
    }

    @PatchMapping("/{userId}/roles")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateRoles(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateRolesRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                accountLifecycleService.updateRoles(actorId(userDetails), userId, request.getRoles()),
                "Account roles updated successfully"));
    }

    private UUID actorId(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
