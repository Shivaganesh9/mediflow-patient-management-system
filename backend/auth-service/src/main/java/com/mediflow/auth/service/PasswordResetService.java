package com.mediflow.auth.service;

import com.mediflow.auth.audit.AuthAuditService;
import com.mediflow.auth.audit.AuthEventTypes;
import com.mediflow.auth.audit.RequestMetadata;
import com.mediflow.auth.config.AuthProperties;
import com.mediflow.auth.dto.PasswordResetConfirmRequest;
import com.mediflow.auth.dto.PasswordResetRequest;
import com.mediflow.auth.dto.PasswordResetResponse;
import com.mediflow.auth.entity.PasswordResetToken;
import com.mediflow.auth.entity.User;
import com.mediflow.auth.repository.PasswordResetTokenRepository;
import com.mediflow.auth.repository.UserRepository;
import com.mediflow.auth.security.TokenHasher;
import com.mediflow.common.enums.AccountStatus;
import com.mediflow.common.exception.AuthenticationFailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Password reset without mail infrastructure.
 *
 * <p>Only a SHA-256 hash of the reset token is stored. The raw token is returned by the
 * API only when {@code auth.password-reset.expose-token=true} (local/dev/test); in any
 * other environment it must be delivered out-of-band, and the API response is identical
 * whether or not the email exists (no account enumeration).</p>
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String GENERIC_MESSAGE =
            "If an account exists for that email, password reset instructions have been generated.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuthProperties authProperties;
    private final SessionRevocationService sessionRevocationService;
    private final AuthAuditService auditService;

    @Transactional
    public PasswordResetResponse requestReset(PasswordResetRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null || user.getStatus() == AccountStatus.DELETED) {
            auditService.record(AuthEventTypes.PASSWORD_RESET_REQUESTED, "FAILURE", null,
                    request.getEmail(), "No eligible account");
            return PasswordResetResponse.builder().message(GENERIC_MESSAGE).build();
        }

        Instant now = Instant.now();
        resetTokenRepository.invalidateOutstanding(user.getId(), now);

        String rawToken = generateToken();
        resetTokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(TokenHasher.sha256(rawToken))
                .expiresAt(now.plus(authProperties.getPasswordReset().getTokenTtl()))
                .requestedIp(RequestMetadata.current().ipAddress())
                .build());

        auditService.record(AuthEventTypes.PASSWORD_RESET_REQUESTED, "SUCCESS",
                user.getId(), user.getUsername(), null);

        return PasswordResetResponse.builder()
                .message(GENERIC_MESSAGE)
                .token(authProperties.getPasswordReset().isExposeToken() ? rawToken : null)
                .build();
    }

    @Transactional
    public void confirmReset(PasswordResetConfirmRequest request) {
        PasswordResetToken token = resetTokenRepository
                .findByTokenHash(TokenHasher.sha256(request.getToken()))
                .orElseThrow(() -> new AuthenticationFailedException("Invalid or expired password reset token"));

        Instant now = Instant.now();
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(now)) {
            throw new AuthenticationFailedException("Invalid or expired password reset token");
        }

        User user = token.getUser();
        if (user.getStatus() == AccountStatus.DELETED) {
            throw new AuthenticationFailedException("Invalid or expired password reset token");
        }
        passwordPolicy.validate(request.getNewPassword(), user.getUsername());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(now);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        token.setUsedAt(now);
        resetTokenRepository.save(token);

        sessionRevocationService.revokeUserSessions(user.getId(), "PASSWORD_RESET");
        auditService.record(AuthEventTypes.PASSWORD_RESET_COMPLETED, "SUCCESS",
                user.getId(), user.getUsername(), null);
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
