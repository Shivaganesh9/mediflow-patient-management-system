package com.mediflow.common.security;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared, low-latency access-token revocation state.
 *
 * <p>Access tokens are stateless and validated locally by every service, so a
 * revocation decision has to be visible to every service. This registry is the
 * single contract: auth-service writes revocations, every resource service reads
 * them during JWT authentication. The only implementation used at runtime is
 * Redis-backed (see {@link RedisTokenRevocationRegistry}).</p>
 *
 * <p>Three granularities are supported:</p>
 * <ul>
 *   <li>a single token ({@code jti}) - e.g. defensive revocation of one token;</li>
 *   <li>a login session ({@code sid} family) - logout of one browser session;</li>
 *   <li>all tokens of a user - password change/reset, role change, suspension,
 *       deactivation or deletion.</li>
 * </ul>
 */
public interface TokenRevocationRegistry {

    /**
     * @param jti       unique token id from the access token
     * @param userId    {@code uid} claim (may be {@code null} for legacy tokens)
     * @param sessionId {@code sid} claim (may be {@code null} for legacy tokens)
     * @param issuedAt  {@code iat} claim; used for user/session "not before" checks
     * @return {@code true} when the token must not be accepted
     */
    boolean isRevoked(String jti, UUID userId, String sessionId, Instant issuedAt);

    /** Revoke one token until it would have expired anyway. */
    void revokeToken(String jti, Instant expiresAt);

    /** Revoke every access token issued in one refresh-token family. */
    void revokeSession(String sessionId, Instant revokedAt);

    /** Revoke every access token issued for a user (all sessions). */
    void revokeAllForUser(UUID userId, Instant revokedAt);
}
