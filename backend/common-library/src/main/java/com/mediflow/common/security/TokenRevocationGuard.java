package com.mediflow.common.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared adapter between a parsed JWT and the {@link TokenRevocationRegistry}.
 *
 * <p>Every service's {@code JwtAuthenticationFilter} calls this immediately after signature
 * validation, which is what makes revocation effective across the whole platform instead
 * of only in auth-service.</p>
 */
@Component
@RequiredArgsConstructor
public class TokenRevocationGuard {

    private final TokenRevocationRegistry registry;

    /**
     * @return {@code true} when the token must be rejected
     * @throws RevocationUnavailableException when the store is unreachable and fail-closed is configured
     */
    public boolean isRevoked(Claims claims) {
        return registry.isRevoked(
                claims.getId(),
                uuid(claims.get(JwtClaims.UID)),
                claims.get(JwtClaims.SESSION_ID, String.class),
                issuedAt(claims));
    }

    /** Access tokens carry {@code type=access}; refresh tokens must never be used as bearer credentials. */
    public boolean isRefreshToken(Claims claims) {
        return JwtClaims.REFRESH.equals(claims.get(JwtClaims.TYPE, String.class));
    }

    private static Instant issuedAt(Claims claims) {
        Object millis = claims.get(JwtClaims.ISSUED_AT_MILLIS);
        if (millis instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }
        return claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant();
    }

    private static UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
