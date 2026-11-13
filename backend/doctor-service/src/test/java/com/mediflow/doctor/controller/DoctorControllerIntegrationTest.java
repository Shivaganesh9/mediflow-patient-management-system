package com.mediflow.doctor.controller;

import com.mediflow.common.exception.ConflictException;
import com.mediflow.doctor.identity.IdentityVerifier;
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
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.secret=test-secret-key-for-doctor-service-with-sufficient-length")
class DoctorControllerIntegrationTest {

    private static final String SECRET = "test-secret-key-for-doctor-service-with-sufficient-length";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityVerifier identityVerifier;

    @Test
    void adminCreatesDoctorForVerifiedIdentity() throws Exception {
        UUID userId = UUID.randomUUID();

        postDoctor(adminToken(), userId, "doctor-" + userId + "@example.com")
                .andExpect(status().isCreated());

        verify(identityVerifier).verifyIdentity(userId, "DOCTOR");
    }

    @Test
    void doctorCreatesOwnProfileWithoutRemoteIdentityVerification() throws Exception {
        UUID selfId = UUID.randomUUID();

        postDoctor(token("dr-jane", selfId, List.of("DOCTOR")), null,
                "self-" + selfId + "@example.com")
                .andExpect(status().isCreated());

        verify(identityVerifier, never()).verifyIdentity(any(), any());
    }

    @Test
    void inactiveIdentityIsRejectedWithConflict() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new ConflictException("The referenced user account is not active"))
                .when(identityVerifier).verifyIdentity(userId, "DOCTOR");

        postDoctor(adminToken(), userId, "doctor-" + userId + "@example.com")
                .andExpect(status().isConflict());
    }

    @Test
    void identityWithWrongRoleIsRejectedWithBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("userId does not reference a DOCTOR identity"))
                .when(identityVerifier).verifyIdentity(userId, "DOCTOR");

        postDoctor(adminToken(), userId, "doctor-" + userId + "@example.com")
                .andExpect(status().isBadRequest());
    }

    @Test
    void patientCannotCreateDoctorProfile() throws Exception {
        UUID userId = UUID.randomUUID();

        postDoctor(token("alice", UUID.randomUUID(), List.of("PATIENT")), userId,
                "doctor-" + userId + "@example.com")
                .andExpect(status().isForbidden());

        verify(identityVerifier, never()).verifyIdentity(any(), any());
    }

    @Test
    void duplicateDoctorProfileForSameIdentityIsRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        postDoctor(adminToken(), userId, "doctor-" + userId + "@example.com")
                .andExpect(status().isCreated());

        postDoctor(adminToken(), userId, "duplicate-" + userId + "@example.com")
                .andExpect(status().isBadRequest());
    }

    @Test
    void nurseCannotCreateDoctorProfile() throws Exception {
        UUID userId = UUID.randomUUID();

        postDoctor(token("nurse1", UUID.randomUUID(), List.of("NURSE")), userId,
                "nurse-" + userId + "@example.com")
                .andExpect(status().isForbidden());

        verify(identityVerifier, never()).verifyIdentity(any(), any());
    }

    @Test
    void supportCannotUpdateDoctorProfile() throws Exception {
        putDoctor(token("support1", UUID.randomUUID(), List.of("SUPPORT")), UUID.randomUUID(),
                "support-update@example.com")
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorCannotUpdateAnotherDoctorsProfile() throws Exception {
        UUID otherDoctorId = UUID.randomUUID();
        UUID selfId = UUID.randomUUID();
        postDoctor(adminToken(), otherDoctorId, "other-" + otherDoctorId + "@example.com")
                .andExpect(status().isCreated());

        putDoctor(token("doctor-self", selfId, List.of("DOCTOR")), otherDoctorId,
                "hijacked-" + otherDoctorId + "@example.com")
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorCanUpdateOwnProfile() throws Exception {
        UUID selfId = UUID.randomUUID();
        String doctorToken = token("doctor-self", selfId, List.of("DOCTOR"));
        postDoctor(doctorToken, null, "self-" + selfId + "@example.com")
                .andExpect(status().isCreated());

        putDoctor(doctorToken, selfId, "self-updated-" + selfId + "@example.com")
                .andExpect(status().isOk());
    }

    @Test
    void unknownRoleIsForbidden() throws Exception {
        UUID userId = UUID.randomUUID();

        postDoctor(token("legacy-staff", UUID.randomUUID(), List.of("STAFF")), userId,
                "legacy-" + userId + "@example.com")
                .andExpect(status().isForbidden());

        verify(identityVerifier, never()).verifyIdentity(any(), any());
    }

    @Test
    void unauthenticatedCreateIsRejected() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorPayload(userId, "anon-" + userId + "@example.com")))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions postDoctor(String token, UUID userId, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/doctors")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(doctorPayload(userId, email)));
    }

    private ResultActions putDoctor(String token, UUID doctorId, String email) throws Exception {
        return mockMvc.perform(put("/api/v1/doctors/" + doctorId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(doctorPayload(null, email)));
    }

    private String doctorPayload(UUID userId, String email) {
        return "{\"firstName\":\"Jane\",\"lastName\":\"Doe\","
                + "\"email\":\"" + email + "\",\"phone\":\"555-0100\","
                + "\"specialty\":\"Cardiology\",\"yearsOfExperience\":12,"
                + "\"department\":\"Cardiology\",\"consultationFee\":150.00"
                + (userId != null ? ",\"userId\":\"" + userId + "\"" : "")
                + "}";
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
