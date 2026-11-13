package com.mediflow.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    /** Access-token lifetime in seconds, used by the frontend to refresh proactively. */
    private long expiresIn;
    private String username;
    private String email;
    private String role;
}
