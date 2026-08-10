package com.mediflow.auth.service;

import com.mediflow.auth.dto.RegisterRequest;
import com.mediflow.auth.dto.UserProfileResponse;
import com.mediflow.auth.entity.Role;
import com.mediflow.auth.entity.User;
import com.mediflow.auth.repository.RefreshTokenRepository;
import com.mediflow.auth.repository.RoleRepository;
import com.mediflow.auth.repository.UserRepository;
import com.mediflow.auth.security.JwtProvider;
import com.mediflow.common.enums.AccountStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

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
    private JwtProvider jwtProvider;

    @InjectMocks
    private AuthServiceImpl authService;

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
    void registerShouldCreateUserWhenInputValid() {
        RegisterRequest request = RegisterRequest.builder()
                .username("alice")
                .email("alice@example.com")
                .password("password123")
                .build();

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(roleRepository.findByName("PATIENT")).thenReturn(Optional.of(Role.builder().name("PATIENT").build()));
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(User.builder()
                .username("alice")
                .email("alice@example.com")
                .password("encoded")
                .status(AccountStatus.ACTIVE)
                .roles(Set.of(Role.builder().name("PATIENT").build()))
                .build());
        when(jwtProvider.generateAccessToken(anyString(), anyList())).thenReturn("access-token");
        when(jwtProvider.generateRefreshToken(anyString())).thenReturn("refresh-token");

        authService.register(request);

        verify(userRepository).save(any(User.class));
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
