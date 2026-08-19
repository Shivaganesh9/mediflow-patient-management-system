package com.mediflow.patient.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.secret=test-secret-key-for-patient-service-with-sufficient-length")
class PatientControllerIntegrationTest {

    private static final String SECRET = "test-secret-key-for-patient-service-with-sufficient-length";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void patientListScopedToOwnProfile() throws Exception {
        UUID patientA = UUID.randomUUID();
        UUID patientB = UUID.randomUUID();
        createPatient(patientA);
        createPatient(patientB);

        String patientAToken = token("alice", patientA, List.of("PATIENT"));
        mockMvc.perform(get("/api/v1/patients")
                        .header("Authorization", "Bearer " + patientAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].patientId").value(patientA.toString()));
    }

    @Test
    void patientListForcesOwnScopeWhenOtherPatientRequested() throws Exception {
        UUID patientA = UUID.randomUUID();
        UUID patientB = UUID.randomUUID();
        createPatient(patientA);
        createPatient(patientB);

        String patientBToken = token("bob", patientB, List.of("PATIENT"));
        mockMvc.perform(get("/api/v1/patients")
                        .header("Authorization", "Bearer " + patientBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].patientId").value(patientB.toString()));
    }

    @Test
    void patientGetRejectsOtherPatientsProfile() throws Exception {
        UUID patientA = UUID.randomUUID();
        UUID patientB = UUID.randomUUID();
        createPatient(patientA);
        createPatient(patientB);

        String patientAToken = token("alice", patientA, List.of("PATIENT"));
        mockMvc.perform(get("/api/v1/patients/" + patientB)
                        .header("Authorization", "Bearer " + patientAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientGetOwnProfile() throws Exception {
        UUID patientA = UUID.randomUUID();
        createPatient(patientA);

        String patientAToken = token("alice", patientA, List.of("PATIENT"));
        mockMvc.perform(get("/api/v1/patients/" + patientA)
                        .header("Authorization", "Bearer " + patientAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientId").value(patientA.toString()));
    }

    @Test
    void adminSeesAllPatients() throws Exception {
        MvcResult before = mockMvc.perform(get("/api/v1/patients")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn();
        int beforeCount = objectMapper.readTree(before.getResponse().getContentAsString())
                .at("/data/totalElements").asInt();

        createPatient(UUID.randomUUID());
        createPatient(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/patients")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(beforeCount + 2));
    }

    private void createPatient(UUID userId) throws Exception {
        String payload = "{\"firstName\":\"Alice\",\"lastName\":\"Smith\","
                + "\"email\":\"u" + userId + "@example.com\",\"phone\":\"555-0100\","
                + "\"userId\":\"" + userId + "\"}";

        mockMvc.perform(post("/api/v1/patients")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
    }

    private String adminToken() {
        return token("admin", UUID.randomUUID(), List.of("ADMIN"));
    }

    private String token(String subject, UUID uid, List<String> roles) {
        JwtBuilder builder = Jwts.builder()
                .subject(subject)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256);
        if (uid != null) {
            builder = builder.claim("uid", uid.toString());
        }
        return builder.compact();
    }
}