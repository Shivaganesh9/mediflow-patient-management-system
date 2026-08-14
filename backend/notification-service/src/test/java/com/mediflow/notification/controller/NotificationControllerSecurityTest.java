package com.mediflow.notification.controller;

import com.mediflow.notification.AbstractNotificationIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerSecurityTest extends AbstractNotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void notificationEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/notifications/" + UUID.randomUUID() + "/read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMalformedToken() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer not-a-jwt-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenSignedWithWrongSecret() throws Exception {
        String token = io.jsonwebtoken.Jwts.builder()
                .subject("patient1")
                .claim("roles", List.of("PATIENT"))
                .claim("uid", UUID.randomUUID().toString())
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "completely-different-wrong-secret-key-value-for-testing".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsValidPatientToken() throws Exception {
        String token = token("patient1", UUID.randomUUID(), List.of("PATIENT"));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
