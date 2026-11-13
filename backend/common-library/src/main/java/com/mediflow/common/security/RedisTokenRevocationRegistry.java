package com.mediflow.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Redis-backed {@link TokenRevocationRegistry}.
 *
 * <p>Redis is used because every service already validates JWTs locally: it needs only
 * one extra low-latency {@code GET} per request (pipelined into two/three lookups) and no
 * change to the trust model. Gateway introspection would centralise validation but would
 * force every service to trust gateway-injected identity headers, a much larger change.</p>
 *
 * <p>Keys:</p>
 * <ul>
 *   <li>{@code {prefix}jti:{jti}} - value {@code 1}, TTL = remaining token lifetime</li>
 *   <li>{@code {prefix}sid:{sid}} - revocation epoch millis, TTL = marker TTL</li>
 *   <li>{@code {prefix}uid:{uid}} - revocation epoch millis, TTL = marker TTL</li>
 * </ul>
 *
 * <p>A token is revoked when its {@code jti} is blacklisted, or when it was issued
 * strictly before a user/session revocation epoch. Tokens minted after the epoch (a fresh
 * login) stay valid, which avoids permanently locking a user out after a password change.</p>
 */
public class RedisTokenRevocationRegistry implements TokenRevocationRegistry {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenRevocationRegistry.class);
    private static final String JTI = "jti:";
    private static final String SESSION = "sid:";
    private static final String USER = "uid:";

    private final StringRedisTemplate redis;
    private final TokenRevocationProperties properties;

    public RedisTokenRevocationRegistry(StringRedisTemplate redis, TokenRevocationProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public boolean isRevoked(String jti, UUID userId, String sessionId, Instant issuedAt) {
        try {
            if (jti != null && Boolean.TRUE.equals(redis.hasKey(key(JTI + jti)))) {
                return true;
            }
            long issuedAtMillis = issuedAt == null ? Long.MIN_VALUE : issuedAt.toEpochMilli();
            if (revokedSince(USER + userId, issuedAtMillis)) {
                return true;
            }
            return sessionId != null && revokedSince(SESSION + sessionId, issuedAtMillis);
        } catch (RuntimeException ex) {
            return handleStoreFailure("check revocation for jti=" + jti, ex);
        }
    }

    @Override
    public void revokeToken(String jti, Instant expiresAt) {
        if (jti == null) {
            return;
        }
        Duration ttl = expiresAt == null
                ? properties.getMarkerTtl()
                : Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            // Already expired: nothing to revoke.
            return;
        }
        write(() -> redis.opsForValue().set(key(JTI + jti), "1", ttl), "revoke jti=" + jti);
    }

    @Override
    public void revokeSession(String sessionId, Instant revokedAt) {
        if (sessionId == null) {
            return;
        }
        write(() -> redis.opsForValue().set(key(SESSION + sessionId),
                String.valueOf(revokedAt.toEpochMilli()), properties.getMarkerTtl()),
                "revoke sid=" + sessionId);
    }

    @Override
    public void revokeAllForUser(UUID userId, Instant revokedAt) {
        if (userId == null) {
            return;
        }
        write(() -> redis.opsForValue().set(key(USER + userId),
                String.valueOf(revokedAt.toEpochMilli()), properties.getMarkerTtl()),
                "revoke uid=" + userId);
    }

    private boolean revokedSince(String keySuffix, long issuedAtMillis) {
        String marker = redis.opsForValue().get(key(keySuffix));
        if (marker == null) {
            return false;
        }
        try {
            long revokedAtMillis = Long.parseLong(marker);
            return issuedAtMillis < revokedAtMillis;
        } catch (NumberFormatException ex) {
            log.warn("Unexpected revocation marker value '{}' for key {}", marker, key(keySuffix));
            return true;
        }
    }

    private boolean handleStoreFailure(String operation, RuntimeException ex) {
        if (properties.isFailOpen()) {
            log.warn("Revocation store unavailable while trying to {}; failing OPEN because "
                    + "security.revocation.fail-open=true. Revoked tokens may be accepted.", operation, ex);
            return false;
        }
        throw new RevocationUnavailableException("Unable to " + operation, ex);
    }

    private void write(Runnable operation, String description) {
        try {
            operation.run();
        } catch (RuntimeException ex) {
            handleStoreFailure(description, ex);
        }
    }

    private String key(String suffix) {
        return properties.getKeyPrefix() + suffix;
    }
}
