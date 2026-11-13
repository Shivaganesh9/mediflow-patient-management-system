package com.mediflow.auth.security;

import com.mediflow.common.security.JwtClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Mints and verifies JWTs.
 *
 * <p>Access tokens carry {@code uid}, {@code roles}, {@code jti}, {@code sid} (refresh-token
 * family), {@code type=access} and a millisecond-precision {@code iat_ms} claim used by
 * shared revocation checks. Refresh tokens carry {@code uid}, {@code jti}, {@code sid} and
 * {@code type=refresh}; the same token can never be used as a bearer credential because the
 * filters reject {@code type=refresh}.</p>
 */
@Component
public class JwtProvider {

    private final SecretKey signingKey;
    private final long accessExpiration;
    private final long refreshExpiration;

    public JwtProvider(@Value("${jwt.secret}") String jwtSecret,
                       @Value("${jwt.expiration:900000}") long accessExpiration,
                       @Value("${jwt.refresh-expiration:604800000}") long refreshExpiration) {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret must be at least 256 bits (32 bytes) for HS256 signing");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    public String generateAccessToken(String username, UUID userId, Collection<String> roles, String sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim(JwtClaims.UID, userId.toString())
                .claim(JwtClaims.ROLES, roles)
                .claim(JwtClaims.TYPE, JwtClaims.ACCESS)
                .claim(JwtClaims.SESSION_ID, sessionId)
                .claim(JwtClaims.ISSUED_AT_MILLIS, now.toEpochMilli())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessExpiration)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(String username, UUID userId, String sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim(JwtClaims.UID, userId.toString())
                .claim(JwtClaims.TYPE, JwtClaims.REFRESH)
                .claim(JwtClaims.SESSION_ID, sessionId)
                .claim(JwtClaims.ISSUED_AT_MILLIS, now.toEpochMilli())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(refreshExpiration)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .clockSkewSeconds(30)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** Describes the access token on the current request (used by logout to revoke one session). */
    public AccessTokenInfo describeAccessToken(String token) {
        Claims claims = parseClaims(token);
        Instant expiresAt = claims.getExpiration() == null
                ? Instant.now()
                : claims.getExpiration().toInstant();
        UUID userId = null;
        Object uid = claims.get(JwtClaims.UID);
        if (uid != null) {
            try {
                userId = UUID.fromString(String.valueOf(uid));
            } catch (IllegalArgumentException ignored) {
                // legacy token without a usable uid claim
            }
        }
        return new AccessTokenInfo(userId, claims.getId(),
                claims.get(JwtClaims.SESSION_ID, String.class), expiresAt);
    }

    public long getAccessExpirationMillis() {
        return accessExpiration;
    }

    public long getRefreshExpirationMillis() {
        return refreshExpiration;
    }

    public record AccessTokenInfo(UUID userId, String jti, String sessionId, Instant expiresAt) {
    }
}
