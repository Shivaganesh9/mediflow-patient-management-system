package com.mediflow.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-for-auth-service-with-sufficient-length";
    private static final long ACCESS_TTL_MS = 900_000L;
    private static final long REFRESH_TTL_MS = 604_800_000L;

    private final JwtProvider jwtProvider = new JwtProvider(SECRET, ACCESS_TTL_MS, REFRESH_TTL_MS);

    @Test
    void accessTokenShouldCarryIdentitySessionTypeAndMillisecondIssuedAt() {
        UUID userId = UUID.randomUUID();
        String sessionId = UUID.randomUUID().toString();

        Claims claims = jwtProvider.parseClaims(
                jwtProvider.generateAccessToken("alice", userId, List.of("PATIENT"), sessionId));

        assertThat(claims.getId()).isNotBlank();
        assertThat(UUID.fromString(claims.getId())).hasToString(claims.getId());
        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("uid", String.class)).isEqualTo(userId.toString());
        assertThat(claims.get("roles", List.class)).containsExactly("PATIENT");
        assertThat(claims.get("type", String.class)).isEqualTo("access");
        assertThat(claims.get("sid", String.class)).isEqualTo(sessionId);
        assertThat(claims.get("iat_ms", Number.class)).isNotNull();
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                .isBetween(ACCESS_TTL_MS - 1_000L, ACCESS_TTL_MS);
    }

    @Test
    void refreshTokenShouldBeTypedAndCarryTheSameSessionButNoRoles() {
        UUID userId = UUID.randomUUID();
        String sessionId = UUID.randomUUID().toString();

        Claims claims = jwtProvider.parseClaims(
                jwtProvider.generateRefreshToken("carol", userId, sessionId));

        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.get("uid", String.class)).isEqualTo(userId.toString());
        assertThat(claims.get("sid", String.class)).isEqualTo(sessionId);
        assertThat(claims.get("roles")).isNull();
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                .isBetween(REFRESH_TTL_MS - 1_000L, REFRESH_TTL_MS);
    }

    @Test
    void everyAccessAndRefreshTokenGetsAUniqueJtiEvenWithinTheSameSecond() {
        UUID userId = UUID.randomUUID();
        List<String> rawTokens = new ArrayList<>();
        List<String> jtis = new ArrayList<>();

        for (int i = 0; i < 25; i++) {
            String sessionId = UUID.randomUUID().toString();
            for (String token : List.of(
                    jwtProvider.generateAccessToken("bob", userId, List.of("DOCTOR"), sessionId),
                    jwtProvider.generateRefreshToken("bob", userId, sessionId))) {
                rawTokens.add(token);
                jtis.add(jwtProvider.parseClaims(token).getId());
            }
        }

        assertThat(new HashSet<>(rawTokens)).hasSize(rawTokens.size());
        assertThat(new HashSet<>(jtis)).hasSize(jtis.size());
    }

    @Test
    void tamperedAndMalformedTokensAreRejected() {
        String accessToken = jwtProvider.generateAccessToken(
                "dave", UUID.randomUUID(), List.of("ADMIN"), UUID.randomUUID().toString());

        assertThatThrownBy(() -> jwtProvider.parseClaims(accessToken + "tampered"))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwtProvider.parseClaims("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtProvider attacker = new JwtProvider(
                "another-secret-key-that-is-long-enough-for-hs256!", ACCESS_TTL_MS, REFRESH_TTL_MS);
        String forged = attacker.generateAccessToken(
                "alice", UUID.randomUUID(), List.of("ADMIN"), UUID.randomUUID().toString());

        assertThatThrownBy(() -> jwtProvider.parseClaims(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtProvider alreadyExpired = new JwtProvider(SECRET, -60_000L, REFRESH_TTL_MS);
        String token = alreadyExpired.generateAccessToken(
                "eve", UUID.randomUUID(), List.of("PATIENT"), UUID.randomUUID().toString());

        assertThatThrownBy(() -> jwtProvider.parseClaims(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void describeAccessTokenExposesRevocationCoordinates() {
        UUID userId = UUID.randomUUID();
        String sessionId = UUID.randomUUID().toString();
        String token = jwtProvider.generateAccessToken("frank", userId, List.of("NURSE"), sessionId);

        JwtProvider.AccessTokenInfo info = jwtProvider.describeAccessToken(token);

        assertThat(info.userId()).isEqualTo(userId);
        assertThat(info.sessionId()).isEqualTo(sessionId);
        assertThat(info.jti()).isNotBlank();
        assertThat(info.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void signingKeyMustBeAtLeast256Bits() {
        assertThatThrownBy(() -> new JwtProvider("too-short", ACCESS_TTL_MS, REFRESH_TTL_MS))
                .isInstanceOf(IllegalStateException.class);
    }
}
