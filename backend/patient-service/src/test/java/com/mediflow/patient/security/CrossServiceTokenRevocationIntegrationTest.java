package com.mediflow.patient.security;

import com.mediflow.common.security.TokenRevocationRegistry;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-service proof of the revocation architecture: auth-service (and the tests below)
 * only write to the shared Redis store; patient-service independently validates the JWT
 * locally and consults the same store. No service-private revocation state is involved.
 */
@SpringBootTest(properties = {
        "security.revocation.enabled=true",
        "jwt.secret=test-secret-key-for-patient-service-with-sufficient-length",
        "spring.datasource.url=jdbc:h2:mem:patientrevocation;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CrossServiceTokenRevocationIntegrationTest {

    private static final String SECRET = "test-secret-key-for-patient-service-with-sufficient-length";
    private static final String AUTHORIZATION = "Authorization";

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenRevocationRegistry revocationRegistry;

    @Test
    void validTokenIsAcceptedBeforeRevocation() throws Exception {
        String token = accessToken(UUID.randomUUID(), UUID.randomUUID().toString(), Instant.now().minusSeconds(5));

        mockMvc.perform(get("/api/v1/patients").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void userLevelRevocationRejectsTokensIssuedBeforeIt() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = accessToken(userId, UUID.randomUUID().toString(), Instant.now().minusSeconds(5));

        revocationRegistry.revokeAllForUser(userId, Instant.now());

        mockMvc.perform(get("/api/v1/patients").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokensIssuedAfterUserRevocationAreAccepted() throws Exception {
        UUID userId = UUID.randomUUID();
        revocationRegistry.revokeAllForUser(userId, Instant.now());

        String freshToken = accessToken(userId, UUID.randomUUID().toString(), Instant.now().plusSeconds(2));

        mockMvc.perform(get("/api/v1/patients").header(AUTHORIZATION, "Bearer " + freshToken))
                .andExpect(status().isOk());
    }

    @Test
    void sessionRevocationOnlyKillsTokensOfThatSession() throws Exception {
        UUID userId = UUID.randomUUID();
        String revokedSession = UUID.randomUUID().toString();
        String otherSession = UUID.randomUUID().toString();
        String revokedToken = accessToken(userId, revokedSession, Instant.now().minusSeconds(5));
        String otherToken = accessToken(userId, otherSession, Instant.now().minusSeconds(5));

        revocationRegistry.revokeSession(revokedSession, Instant.now());

        mockMvc.perform(get("/api/v1/patients").header(AUTHORIZATION, "Bearer " + revokedToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients").header(AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk());
    }

    @Test
    void individuallyRevokedJtiIsRejected() throws Exception {
        UUID jti = UUID.randomUUID();
        Instant issuedAt = Instant.now().minusSeconds(5);
        String token = accessToken(UUID.randomUUID(), UUID.randomUUID().toString(), issuedAt, jti);

        revocationRegistry.revokeToken(jti.toString(), Instant.now().plusSeconds(600));

        mockMvc.perform(get("/api/v1/patients").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokensCannotBeUsedAsBearerCredentials() throws Exception {
        String refreshToken = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("patient-user")
                .claim("uid", UUID.randomUUID().toString())
                .claim("type", "refresh")
                .claim("sid", UUID.randomUUID().toString())
                .claim("iat_ms", Instant.now().toEpochMilli())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/v1/patients").header(AUTHORIZATION, "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureMalformedAndMissingTokensAreRejected() throws Exception {
        String forged = Jwts.builder()
                .subject("intruder")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(
                        "another-secret-key-that-is-long-enough-for-hs256!".getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/v1/patients").header(AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients").header(AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients"))
                .andExpect(status().isUnauthorized());
    }

    private String accessToken(UUID userId, String sessionId, Instant issuedAt) {
        return accessToken(userId, sessionId, issuedAt, UUID.randomUUID());
    }

    private String accessToken(UUID userId, String sessionId, Instant issuedAt, UUID jti) {
        return Jwts.builder()
                .id(jti.toString())
                .subject("patient-user")
                .claim("uid", userId.toString())
                .claim("roles", List.of("PATIENT"))
                .claim("type", "access")
                .claim("sid", sessionId)
                .claim("iat_ms", issuedAt.toEpochMilli())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }
}
