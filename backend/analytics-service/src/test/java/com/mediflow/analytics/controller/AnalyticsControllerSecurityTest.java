package com.mediflow.analytics.controller;

import com.mediflow.analytics.AbstractAnalyticsIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsControllerSecurityTest extends AbstractAnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/dashboard/summary")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithWrongSecretIsRejected() throws Exception {
        String wrongSecret = "a-completely-different-secret-key-that-is-also-long-enough";
        String token = io.jsonwebtoken.Jwts.builder()
                .subject("hacker")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 600_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(wrongSecret.getBytes(StandardCharsets.UTF_8)),
                        io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/v1/analytics/dashboard/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patientTokenIsForbidden() throws Exception {
        String patientToken = token("patient1", UUID.randomUUID(), List.of("PATIENT"));

        mockMvc.perform(get("/api/v1/analytics/dashboard/summary")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTokenIsAuthorized() throws Exception {
        String adminToken = token("admin", null, List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/analytics/dashboard/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void doctorTokenIsAuthorized() throws Exception {
        String doctorToken = token("doctor1", UUID.randomUUID(), List.of("DOCTOR"));

        mockMvc.perform(get("/api/v1/analytics/revenue")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String expiredToken = io.jsonwebtoken.Jwts.builder()
                .subject("admin")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(new java.util.Date(System.currentTimeMillis() - 60_000))
                .expiration(new java.util.Date(System.currentTimeMillis() - 30_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                                AbstractAnalyticsIntegrationTest.SECRET.getBytes(StandardCharsets.UTF_8)),
                        io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/v1/analytics/dashboard/summary")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }
}