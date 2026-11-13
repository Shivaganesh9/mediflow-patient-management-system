package com.mediflow.auth.service;

import com.mediflow.auth.repository.RefreshTokenRepository;
import com.mediflow.common.security.TokenRevocationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Single place where a security-sensitive change turns into "all tokens are dead".
 *
 * <p>Revocation happens in two stores: the refresh-token rows (so rotation cannot mint a
 * new access token) and the shared access-token registry (so every service rejects the
 * already-issued access tokens). Both must happen for the change to be effective.</p>
 */
@Service
@RequiredArgsConstructor
public class SessionRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenRevocationRegistry revocationRegistry;

    /** Revokes every session of a user: password change/reset, role change, suspension, deactivation. */
    public void revokeUserSessions(UUID userId, String reason) {
        Instant now = Instant.now();
        refreshTokenRepository.revokeAllForUser(userId, now, reason);
        revocationRegistry.revokeAllForUser(userId, now);
    }

    /** Revokes one login session (refresh-token family) - used for logout and reuse detection. */
    public void revokeFamily(UUID familyId, String reason) {
        Instant now = Instant.now();
        refreshTokenRepository.revokeFamily(familyId, now, reason);
        revocationRegistry.revokeSession(familyId.toString(), now);
    }
}
