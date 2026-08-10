package com.mediflow.auth.service;

import com.mediflow.auth.dto.AuthResponse;
import com.mediflow.auth.dto.LoginRequest;
import com.mediflow.auth.dto.RefreshTokenRequest;
import com.mediflow.auth.dto.RegisterRequest;
import com.mediflow.auth.dto.UserProfileResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
    void logout(String username);
    UserProfileResponse getCurrentUser(String username);
}
