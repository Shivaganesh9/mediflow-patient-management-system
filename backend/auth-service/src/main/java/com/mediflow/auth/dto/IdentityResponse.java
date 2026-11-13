package com.mediflow.auth.dto;

import com.mediflow.common.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

/**
 * Minimal identity projection returned by the internal service-to-service lookup.
 * Deliberately excludes credentials and contact data: only the fields needed to
 * validate a profile reference (id, status, roles) are exposed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityResponse {
    private UUID userId;
    private String username;
    private AccountStatus status;
    private Set<String> roles;
}
