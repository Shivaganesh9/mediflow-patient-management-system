package com.mediflow.auth.service;

import com.mediflow.auth.dto.AuthResponse;
import com.mediflow.auth.dto.ChangePasswordRequest;
import com.mediflow.auth.dto.IdentityResponse;
import com.mediflow.auth.dto.LoginRequest;
import com.mediflow.auth.dto.RefreshTokenRequest;
import com.mediflow.auth.dto.RegisterRequest;
import com.mediflow.auth.dto.UserProfileResponse;
import com.mediflow.auth.security.JwtProvider;

import java.util.UUID;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
    void logout(JwtProvider.AccessTokenInfo accessToken);
    UserProfileResponse getCurrentUser(String username);
    IdentityResponse getIdentity(UUID userId);
    void changePassword(String username, ChangePasswordRequest request);
}
