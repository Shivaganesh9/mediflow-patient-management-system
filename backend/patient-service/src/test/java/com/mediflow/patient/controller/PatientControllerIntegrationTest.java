package com.mediflow.patient.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.exception.ConflictException;
import com.mediflow.patient.identity.IdentityVerifier;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @MockitoBean
    private IdentityVerifier identityVerifier;

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

    @Test
    void adminCreateVerifiesReferencedPatientIdentity() throws Exception {
        UUID userId = UUID.randomUUID();

        createPatient(userId);

        verify(identityVerifier).verifyIdentity(userId, "PATIENT");
    }

    @Test
    void inactiveIdentityIsRejectedWithConflict() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new ConflictException("The referenced user account is not active"))
                .when(identityVerifier).verifyIdentity(userId, "PATIENT");

        postPatient(adminToken(), userId, "u" + userId + "@example.com")
                .andExpect(status().isConflict());
    }

    @Test
    void identityWithWrongRoleIsRejectedWithBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("userId does not reference a PATIENT identity"))
                .when(identityVerifier).verifyIdentity(userId, "PATIENT");

        postPatient(adminToken(), userId, "u" + userId + "@example.com")
                .andExpect(status().isBadRequest());
    }

    @Test
    void patientCannotCreateProfileForAnotherIdentity() throws Exception {
        UUID selfId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        postPatient(token("alice", selfId, List.of("PATIENT")), otherUserId,
                "u" + otherUserId + "@example.com")
                .andExpect(status().isForbidden());

        verify(identityVerifier, never()).verifyIdentity(any(), any());
    }

    @Test
    void duplicateProfileForSameIdentityIsRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        createPatient(userId);

        postPatient(adminToken(), userId, "duplicate-" + userId + "@example.com")
                .andExpect(status().isBadRequest());
    }

    @Test
    void nurseCannotCreatePatientProfile() throws Exception {
        UUID userId = UUID.randomUUID();

        postPatient(token("nurse1", UUID.randomUUID(), List.of("NURSE")), userId,
                "nurse-" + userId + "@example.com")
                .andExpect(status().isForbidden());

        verify(identityVerifier, never()).verifyIdentity(any(), any());
    }

    @Test
    void supportCannotUpdatePatientProfile() throws Exception {
        UUID patientId = UUID.randomUUID();
        createPatient(patientId);

        putPatient(token("support1", UUID.randomUUID(), List.of("SUPPORT")), patientId,
                "support-update-" + patientId + "@example.com")
                .andExpect(status().isForbidden());
    }

    @Test
    void billingStaffCannotUpdatePatientProfile() throws Exception {
        UUID patientId = UUID.randomUUID();
        createPatient(patientId);

        putPatient(token("billing1", UUID.randomUUID(), List.of("BILLING_STAFF")), patientId,
                "billing-update-" + patientId + "@example.com")
                .andExpect(status().isForbidden());
    }

    @Test
    void patientCannotDeleteOwnProfile() throws Exception {
        UUID patientId = UUID.randomUUID();
        createPatient(patientId);

        mockMvc.perform(delete("/api/v1/patients/" + patientId)
                        .header("Authorization", "Bearer "
                                + token("alice", patientId, List.of("PATIENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownRoleIsForbidden() throws Exception {
        UUID userId = UUID.randomUUID();

        postPatient(token("legacy-staff", UUID.randomUUID(), List.of("STAFF")), userId,
                "legacy-" + userId + "@example.com")
                .andExpect(status().isForbidden());

        verify(identityVerifier, never()).verifyIdentity(any(), any());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/patients"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions putPatient(String token, UUID patientId, String email) throws Exception {
        String payload = "{\"firstName\":\"Alice\",\"lastName\":\"Smith\","
                + "\"email\":\"" + email + "\",\"phone\":\"555-0100\"}";

        return mockMvc.perform(put("/api/v1/patients/" + patientId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
    }

    private ResultActions postPatient(String token, UUID userId, String email) throws Exception {
        String payload = "{\"firstName\":\"Alice\",\"lastName\":\"Smith\","
                + "\"email\":\"" + email + "\",\"phone\":\"555-0100\","
                + "\"userId\":\"" + userId + "\"}";

        return mockMvc.perform(post("/api/v1/patients")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
    }

    private void createPatient(UUID userId) throws Exception {
        postPatient(adminToken(), userId, "u" + userId + "@example.com")
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