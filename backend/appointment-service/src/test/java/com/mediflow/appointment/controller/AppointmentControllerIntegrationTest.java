package com.mediflow.appointment.controller;

import com.mediflow.appointment.event.AppointmentEventPublisher;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentControllerIntegrationTest {

    private static final String SECRET = "test-secret-key-for-appointment-service-with-sufficient-length";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppointmentEventPublisher eventPublisher;

    private String token;

    @BeforeEach
    void setUp() {
        token = Jwts.builder()
                .subject("doctor1")
                .claim("roles", List.of("DOCTOR"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void createAppointmentReturnsCreated() throws Exception {
        String payload = "{\"patientId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"doctorId\":\"22222222-2222-2222-2222-222222222222\","
                + "\"startTime\":\"2030-01-01T10:00:00\",\"endTime\":\"2030-01-01T11:00:00\","
                + "\"reason\":\"Checkup\"}";

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
    }

    @Test
    void createAppointmentRejectsEndBeforeStart() throws Exception {
        String payload = "{\"patientId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"doctorId\":\"22222222-2222-2222-2222-222222222222\","
                + "\"startTime\":\"2030-01-01T11:00:00\",\"endTime\":\"2030-01-01T10:00:00\","
                + "\"reason\":\"Checkup\"}";

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAppointmentRejectsMissingRequiredFields() throws Exception {
        String payload = "{\"reason\":\"Checkup\"}";

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMissingAppointmentReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateMissingAppointmentReturnsNotFound() throws Exception {
        String payload = "{\"startTime\":\"2030-01-01T10:00:00\",\"endTime\":\"2030-01-01T11:00:00\"}";

        mockMvc.perform(put("/api/v1/appointments/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());
    }
}
