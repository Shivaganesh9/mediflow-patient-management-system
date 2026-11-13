package com.mediflow.auth.service;

import com.mediflow.auth.audit.AuthAuditService;
import com.mediflow.auth.audit.AuthEventTypes;
import com.mediflow.auth.config.AuthProperties;
import com.mediflow.auth.dto.AuthResponse;
import com.mediflow.auth.dto.ChangePasswordRequest;
import com.mediflow.auth.dto.IdentityResponse;
import com.mediflow.auth.dto.LoginRequest;
import com.mediflow.auth.dto.RefreshTokenRequest;
import com.mediflow.auth.dto.RegisterRequest;
import com.mediflow.auth.dto.UserProfileResponse;
import com.mediflow.auth.entity.RefreshToken;
import com.mediflow.auth.entity.Role;
import com.mediflow.auth.entity.User;
import com.mediflow.auth.repository.RefreshTokenRepository;
import com.mediflow.auth.repository.RoleRepository;
import com.mediflow.auth.repository.UserRepository;
import com.mediflow.auth.security.JwtProvider;
import com.mediflow.auth.security.TokenHasher;
import com.mediflow.common.enums.AccountStatus;
import com.mediflow.common.exception.AuthenticationFailedException;
import com.mediflow.common.exception.InactiveAccountException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.common.security.TokenRevocationRegistry;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String INVALID_REFRESH = "Invalid or expired refresh token";

    /**
     * BCrypt hash of a throwaway value, verified when the username does not exist so the
     * response time matches a wrong-password attempt (no account enumeration by timing).
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final TokenRevocationRegistry revocationRegistry;
    private final LoginAttemptService loginAttemptService;
    private final SessionRevocationService sessionRevocationService;
    private final AuthAuditService auditService;
    private final PasswordPolicy passwordPolicy;
    private final AuthProperties authProperties;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }
        passwordPolicy.validate(request.getPassword(), request.getUsername());

        Role role = roleRepository.findByName("PATIENT")
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name("PATIENT").description("Default patient role").build()));

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .status(AccountStatus.ACTIVE)
                .passwordChangedAt(Instant.now())
                .roles(Set.of(role))
                .build();

        user = userRepository.save(user);
        AuthResponse response = issueSession(user);
        auditService.record(AuthEventTypes.REGISTERED, "SUCCESS", user.getId(), user.getUsername(), null);
        return response;
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsernameOrEmail(request.getUsernameOrEmail()).orElse(null);

        // Fail fast while locked: avoids paying BCrypt cost for every flooded attempt.
        loginAttemptService.assertNotLocked(user);

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    request.getUsernameOrEmail(), request.getPassword()));
        } catch (AuthenticationException ex) {
            if (user != null) {
                loginAttemptService.recordFailure(user.getId());
            } else {
                // Unknown username: still pay one BCrypt verification so response time cannot
                // be used to enumerate accounts. The dummy hash never matches a real password.
                passwordEncoder.matches(request.getPassword(), DUMMY_PASSWORD_HASH);
            }
            auditService.record(AuthEventTypes.LOGIN_FAILED, "FAILURE",
                    user == null ? null : user.getId(), request.getUsernameOrEmail(), "Invalid credentials");
            // Same message whether the user exists or the password was wrong.
            throw new AuthenticationFailedException("Invalid username or password");
        }

        if (user == null) {
            throw new AuthenticationFailedException("Invalid username or password");
        }

        loginAttemptService.recordSuccess(user.getId());
        requireActive(user);

        AuthResponse response = issueSession(user);
        auditService.record(AuthEventTypes.LOGIN_SUCCEEDED, "SUCCESS", user.getId(), user.getUsername(), null);
        return response;
    }

    /**
     * The reuse/expiry/inactive branches revoke state and then fail the request. Those
     * revocations must survive the failure, so the transactional rollback is disabled for
     * the authentication exceptions; everything else still rolls back normally.
     */
    @Override
    @Transactional(noRollbackFor = {AuthenticationFailedException.class, InactiveAccountException.class})
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        Claims claims;
        try {
            claims = jwtProvider.parseClaims(request.getRefreshToken());
        } catch (RuntimeException ex) {
            auditService.record(AuthEventTypes.REFRESH_REJECTED, "FAILURE", null, null,
                    "Signature or expiry validation failed");
            throw new AuthenticationFailedException(INVALID_REFRESH);
        }

        if (!JwtClaims.REFRESH.equals(claims.get(JwtClaims.TYPE, String.class))) {
            auditService.record(AuthEventTypes.REFRESH_REJECTED, "FAILURE", null, null,
                    "Access token presented at refresh endpoint");
            throw new AuthenticationFailedException(INVALID_REFRESH);
        }

        String jti = claims.getId();
        RefreshToken presented = refreshTokenRepository.findByTokenHash(TokenHasher.sha256(request.getRefreshToken()))
                .filter(token -> token.getJti().equals(jti))
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_REFRESH));

        // Row lock: serialises concurrent refreshes of the same token.
        RefreshToken locked = refreshTokenRepository.findByJtiForUpdate(presented.getJti())
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_REFRESH));

        User user = locked.getUser();
        // The token->user association is lazy. Resolve the audit/status values up front:
        // the bulk revocation below clears the persistence context, which would detach
        // this proxy and turn the security response into a 500.
        UUID userId = user.getId();
        String username = user.getUsername();
        AccountStatus accountStatus = user.getStatus();
        Instant now = Instant.now();

        if (locked.isRevoked()) {
            if (isBenignRetry(locked, now)) {
                log.debug("Treating reuse of jti {} as a benign retry within the grace window", locked.getJti());
                return rotateFamilyActiveToken(user, locked.getFamilyId(), now);
            }
            // Genuine reuse: assume the token was stolen and kill the whole family.
            sessionRevocationService.revokeFamily(locked.getFamilyId(), "REUSE_DETECTED");
            auditService.record(AuthEventTypes.REFRESH_REUSE_DETECTED, "BLOCKED",
                    userId, username, "family=" + locked.getFamilyId());
            log.warn("Refresh token reuse detected for user {} family {}; family revoked",
                    username, locked.getFamilyId());
            throw new AuthenticationFailedException(INVALID_REFRESH);
        }

        if (locked.getExpiryDate().isBefore(now)) {
            locked.setRevoked(true);
            locked.setRevokedAt(now);
            locked.setRevokeReason("EXPIRED");
            refreshTokenRepository.save(locked);
            throw new AuthenticationFailedException(INVALID_REFRESH);
        }

        if (!AccountStatus.ACTIVE.equals(accountStatus)) {
            sessionRevocationService.revokeFamily(locked.getFamilyId(), "ACCOUNT_" + accountStatus);
            throw new InactiveAccountException(accountStatus);
        }

        return rotate(user, locked, now);
    }

    @Override
    @Transactional
    public void logout(JwtProvider.AccessTokenInfo accessToken) {
        if (accessToken.userId() == null) {
            return;
        }
        UUID familyId = parseUuid(accessToken.sessionId());
        if (familyId != null) {
            sessionRevocationService.revokeFamily(familyId, "LOGOUT");
        } else {
            // Legacy token without sid: be conservative and end every session for the user.
            sessionRevocationService.revokeUserSessions(accessToken.userId(), "LOGOUT");
        }
        // Also blacklist the exact access token, covering the window until it expires.
        revocationRegistry.revokeToken(accessToken.jti(), accessToken.expiresAt());

        String username = userRepository.findById(accessToken.userId())
                .map(User::getUsername)
                .orElse(null);
        auditService.record(AuthEventTypes.LOGOUT, "SUCCESS", accessToken.userId(), username, null);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toProfile(user);
    }

    @Override
    @Transactional(readOnly = true)
    public IdentityResponse getIdentity(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return IdentityResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .status(user.getStatus())
                .roles(roleNames(user))
                .build();
    }

    @Override
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            auditService.record(AuthEventTypes.PASSWORD_CHANGED, "FAILURE",
                    user.getId(), user.getUsername(), "Current password incorrect");
            throw new AuthenticationFailedException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }
        passwordPolicy.validate(request.getNewPassword(), user.getUsername());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(Instant.now());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        sessionRevocationService.revokeUserSessions(user.getId(), "PASSWORD_CHANGED");
        auditService.record(AuthEventTypes.PASSWORD_CHANGED, "SUCCESS", user.getId(), user.getUsername(), null);
    }

    // ---------------------------------------------------------------------
    // Token issuing / rotation
    // ---------------------------------------------------------------------

    private AuthResponse issueSession(User user) {
        UUID familyId = UUID.randomUUID();
        String refreshToken = jwtProvider.generateRefreshToken(
                user.getUsername(), user.getId(), familyId.toString());
        persistRefreshToken(user, refreshToken, familyId, null);

        String accessToken = jwtProvider.generateAccessToken(
                user.getUsername(), user.getId(), roleNames(user), familyId.toString());
        return buildResponse(user, accessToken, refreshToken);
    }

    private AuthResponse rotate(User user, RefreshToken current, Instant now) {
        UUID familyId = current.getFamilyId();
        String newRefreshToken = jwtProvider.generateRefreshToken(
                user.getUsername(), user.getId(), familyId.toString());
        Claims newClaims = jwtProvider.parseClaims(newRefreshToken);

        current.setRevoked(true);
        current.setRevokedAt(now);
        current.setRevokeReason("ROTATED");
        current.setReplacedByJti(newClaims.getId());
        refreshTokenRepository.save(current);

        persistRefreshToken(user, newRefreshToken, familyId, newClaims.getId());

        String accessToken = jwtProvider.generateAccessToken(
                user.getUsername(), user.getId(), roleNames(user), familyId.toString());
        auditService.record(AuthEventTypes.TOKEN_REFRESHED, "SUCCESS", user.getId(), user.getUsername(), null);
        return buildResponse(user, accessToken, newRefreshToken);
    }

    /**
     * A duplicate refresh arriving inside the grace window is treated as a retry. The
     * family's current active token is rotated instead of accepting the already-rotated
     * token, so the family never exposes two independently usable chains.
     */
    private AuthResponse rotateFamilyActiveToken(User user, UUID familyId, Instant now) {
        RefreshToken active = refreshTokenRepository
                .findFirstByFamilyIdAndRevokedFalseOrderByCreatedAtDesc(familyId)
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_REFRESH));
        RefreshToken locked = refreshTokenRepository.findByJtiForUpdate(active.getJti())
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_REFRESH));
        if (locked.isRevoked() || locked.getExpiryDate().isBefore(now)) {
            throw new AuthenticationFailedException(INVALID_REFRESH);
        }
        return rotate(user, locked, now);
    }

    private void persistRefreshToken(User user, String rawToken, UUID familyId, String jtiOverride) {
        Claims claims = jwtProvider.parseClaims(rawToken);
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(TokenHasher.sha256(rawToken))
                .jti(jtiOverride == null ? claims.getId() : jtiOverride)
                .familyId(familyId)
                .expiryDate(claims.getExpiration().toInstant())
                .revoked(false)
                .build());
    }

    private boolean isBenignRetry(RefreshToken token, Instant now) {
        return "ROTATED".equals(token.getRevokeReason())
                && token.getReplacedByJti() != null
                && token.getRevokedAt() != null
                && token.getRevokedAt().isAfter(now.minus(authProperties.getRefresh().getReuseGrace()));
    }

    private void requireActive(User user) {
        if (!AccountStatus.ACTIVE.equals(user.getStatus())) {
            auditService.record(AuthEventTypes.LOGIN_BLOCKED, "BLOCKED",
                    user.getId(), user.getUsername(), "status=" + user.getStatus());
            throw new InactiveAccountException(user.getStatus());
        }
    }

    private AuthResponse buildResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProvider.getAccessExpirationMillis() / 1000)
                .username(user.getUsername())
                .email(user.getEmail())
                .role(primaryRole(user))
                .build();
    }

    private UserProfileResponse toProfile(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .status(user.getStatus())
                .roles(roleNames(user))
                .build();
    }

    private Set<String> roleNames(User user) {
        return user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String primaryRole(User user) {
        return Optional.ofNullable(user.getRoles()).orElse(Set.of()).stream()
                .map(Role::getName)
                .sorted()
                .findFirst()
                .orElse("PATIENT");
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
