package com.mediflow.auth.service;

import com.mediflow.auth.audit.AuthAuditService;
import com.mediflow.auth.config.AuthProperties;
import com.mediflow.auth.dto.RegisterRequest;
import com.mediflow.auth.dto.UserProfileResponse;
import com.mediflow.auth.entity.Role;
import com.mediflow.auth.entity.User;
import com.mediflow.auth.repository.RefreshTokenRepository;
import com.mediflow.auth.repository.RoleRepository;
import com.mediflow.auth.repository.UserRepository;
import com.mediflow.auth.security.JwtProvider;
import com.mediflow.common.enums.AccountStatus;
import com.mediflow.common.security.TokenRevocationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String SECRET = "test-secret-key-for-auth-service-with-sufficient-length";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenRevocationRegistry revocationRegistry;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private AuthAuditService auditService;

    @Mock
    private PasswordPolicy passwordPolicy;

    @Mock
    private AuthProperties authProperties;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        JwtProvider jwtProvider = new JwtProvider(SECRET, 900_000L, 604_800_000L);
        SessionRevocationService sessionRevocationService =
                new SessionRevocationService(refreshTokenRepository, revocationRegistry);
        authService = new AuthServiceImpl(userRepository, roleRepository, refreshTokenRepository,
                passwordEncoder, authenticationManager, jwtProvider, revocationRegistry,
                loginAttemptService, sessionRevocationService, auditService, passwordPolicy, authProperties);
    }

    @Test
    void registerShouldThrowWhenUsernameExists() {
        RegisterRequest request = RegisterRequest.builder()
                .username("alice")
                .email("alice@example.com")
                .password("password123")
                .build();

        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
    }

    @Test
    void registerShouldCreateUserAndIssueARevocableSession() {
        RegisterRequest request = RegisterRequest.builder()
                .username("alice")
                .email("alice@example.com")
                .password("password123")
                .build();

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(roleRepository.findByName("PATIENT"))
                .thenReturn(Optional.of(Role.builder().name("PATIENT").build()));
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.register(request);

        verify(userRepository).save(any(User.class));
        verify(refreshTokenRepository).save(any(com.mediflow.auth.entity.RefreshToken.class));
        assertEquals("Bearer", response.getTokenType());
        assertEquals(900L, response.getExpiresIn());
        assertEquals("alice", response.getUsername());
        assertEquals("PATIENT", response.getRole());
        org.assertj.core.api.Assertions.assertThat(response.getAccessToken()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(response.getRefreshToken()).isNotBlank();
    }

    @Test
    void currentUserShouldReturnProfileWithoutPassword() {
        User user = User.builder()
                .username("alice")
                .email("alice@example.com")
                .password("encoded-password")
                .fullName("Alice Smith")
                .status(AccountStatus.ACTIVE)
                .roles(Set.of(Role.builder().name("PATIENT").build()))
                .build();
        UUID userId = user.getId();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserProfileResponse profile = authService.getCurrentUser("alice");

        assertEquals(userId, profile.getId());
        assertEquals("alice", profile.getUsername());
        assertEquals(Set.of("PATIENT"), profile.getRoles());
    }
}
