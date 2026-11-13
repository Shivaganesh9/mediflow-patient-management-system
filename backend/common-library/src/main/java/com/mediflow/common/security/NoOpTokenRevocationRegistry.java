package com.mediflow.common.security;

import java.time.Instant;
import java.util.UUID;

/**
 * Explicit opt-out used only by tests and by local profiles where
 * {@code security.revocation.enabled=false}. Never enable this in production:
 * revoked tokens would remain valid until they expire.
 */
public class NoOpTokenRevocationRegistry implements TokenRevocationRegistry {

    @Override
    public boolean isRevoked(String jti, UUID userId, String sessionId, Instant issuedAt) {
        return false;
    }

    @Override
    public void revokeToken(String jti, Instant expiresAt) {
        // no-op
    }

    @Override
    public void revokeSession(String sessionId, Instant revokedAt) {
        // no-op
    }

    @Override
    public void revokeAllForUser(UUID userId, Instant revokedAt) {
        // no-op
    }
}
