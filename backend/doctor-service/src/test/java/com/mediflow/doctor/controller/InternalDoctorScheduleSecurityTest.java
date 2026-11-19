package com.mediflow.doctor.controller;

import com.mediflow.common.security.InternalServiceToken;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.doctor.identity.IdentityVerifier;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security regression tests for the non-gateway internal schedule endpoint.
 *
 * <p>The contract is: only appointment-service's derived-key service token with the
 * {@code doctor-schedule:validate} scope may call {@code /internal/**}; user tokens (any role),
 * expired or tampered credentials, and wrongly scoped/issued credentials are rejected.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.secret=" + InternalDoctorScheduleSecurityTest.SECRET)
class InternalDoctorScheduleSecurityTest {

    static final String SECRET = "test-secret-key-for-internal-schedule-security";

    private static final String PATH = "/internal/doctors/{id}/schedule/validation";
    private static final LocalDateTime START = LocalDateTime.of(2035, 5, 7, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2035, 5, 7, 10, 30);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityVerifier identityVerifier;

    @Test
    void acceptsAValidAppointmentServiceCredential() throws Exception {
        UUID doctorId = createDoctorProfile();

        mockMvc.perform(get(PATH, doctorId)
                        .header("Authorization", "Bearer " + serviceToken(
                                InternalServiceToken.SCOPE_DOCTOR_SCHEDULE_VALIDATE,
                                InternalServiceToken.ISSUER_APPOINTMENT_SERVICE,
                                InternalServiceToken.AUDIENCE_DOCTOR_SERVICE,
                                60))
                        .param("startTime", START.toString())
                        .param("endTime", END.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").isBoolean());
    }

    @Test
    void rejectsRequestsWithoutACredential() throws Exception {
        mockMvc.perform(get(PATH, UUID.randomUUID())
                        .param("startTime", START.toString())
                        .param("endTime", END.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUserTokensFromEveryRole() throws Exception {
        // Doctors, admins, patients and staff are all ordinary users on this contract.
        for (String role : List.of("PATIENT", "DOCTOR", "ADMIN", "NURSE", "BILLING_STAFF", "SUPPORT")) {
            mockMvc.perform(get(PATH, UUID.randomUUID())
                            .header("Authorization", "Bearer " + userToken(role))
                            .param("startTime", START.toString())
                            .param("endTime", END.toString()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void rejectsAMissingOrMalformedCredential() throws Exception {
        mockMvc.perform(get(PATH, UUID.randomUUID())
                        .header("Authorization", "Bearer not-a-jwt")
                        .param("startTime", START.toString())
                        .param("endTime", END.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnExpiredServiceCredential() throws Exception {
        String expired = Jwts.builder()
                .subject(InternalServiceToken.ISSUER_APPOINTMENT_SERVICE)
                .issuer(InternalServiceToken.ISSUER_APPOINTMENT_SERVICE)
                .audience().add(InternalServiceToken.AUDIENCE_DOCTOR_SERVICE).and()
                .claim(JwtClaims.TYPE, InternalServiceToken.TYPE_SERVICE)
                .claim(InternalServiceToken.CLAIM_SCOPE, InternalServiceToken.SCOPE_DOCTOR_SCHEDULE_VALIDATE)
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(InternalServiceToken.signingKey(SECRET), Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get(PATH, UUID.randomUUID())
                        .header("Authorization", "Bearer " + expired)
                        .param("startTime", START.toString())
                        .param("endTime", END.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsACredentialSignedWithTheUserKey() throws Exception {
        // A token signed with the raw user secret can authenticate as a (roleless) user, but the
        // derived service key is different, so it never receives the internal scope and is forbidden.
        String rawKeyToken = Jwts.builder()
                .subject(InternalServiceToken.ISSUER_APPOINTMENT_SERVICE)
                .issuer(InternalServiceToken.ISSUER_APPOINTMENT_SERVICE)
                .audience().add(InternalServiceToken.AUDIENCE_DOCTOR_SERVICE).and()
                .claim(JwtClaims.TYPE, InternalServiceToken.TYPE_SERVICE)
                .claim(InternalServiceToken.CLAIM_SCOPE, InternalServiceToken.SCOPE_DOCTOR_SCHEDULE_VALIDATE)
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get(PATH, UUID.randomUUID())
                        .header("Authorization", "Bearer " + rawKeyToken)
                        .param("startTime", START.toString())
                        .param("endTime", END.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsACredentialWithTheWrongScope() throws Exception {
        mockMvc.perform(get(PATH, UUID.randomUUID())
                        .header("Authorization", "Bearer " + serviceToken(
                                "patient-records:read",
                                InternalServiceToken.ISSUER_APPOINTMENT_SERVICE,
                                InternalServiceToken.AUDIENCE_DOCTOR_SERVICE,
                                60))
                        .param("startTime", START.toString())
                        .param("endTime", END.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsACredentialForAnotherAudienceOrIssuer() throws Exception {
        String wrongAudience = serviceToken(
                InternalServiceToken.SCOPE_DOCTOR_SCHEDULE_VALIDATE,
                InternalServiceToken.ISSUER_APPOINTMENT_SERVICE,
                "billing-service",
                60);
        String wrongIssuer = serviceToken(
                InternalServiceToken.SCOPE_DOCTOR_SCHEDULE_VALIDATE,
                "medical-record-service",
                InternalServiceToken.AUDIENCE_DOCTOR_SERVICE,
                60);

        for (String token : List.of(wrongAudience, wrongIssuer)) {
            mockMvc.perform(get(PATH, UUID.randomUUID())
                            .header("Authorization", "Bearer " + token)
                            .param("startTime", START.toString())
                            .param("endTime", END.toString()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void aServiceCredentialCannotAccessUserEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/doctors")
                        .header("Authorization", "Bearer " + serviceToken(
                                InternalServiceToken.SCOPE_DOCTOR_SCHEDULE_VALIDATE,
                                InternalServiceToken.ISSUER_APPOINTMENT_SERVICE,
                                InternalServiceToken.AUDIENCE_DOCTOR_SERVICE,
                                60)))
                .andExpect(status().isUnauthorized());
    }

    private UUID createDoctorProfile() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/doctors")
                        .header("Authorization", "Bearer " + userToken("DOCTOR", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Internal\",\"lastName\":\"Caller\","
                                + "\"email\":\"internal-" + id + "@example.com\",\"phone\":\"555-0199\","
                                + "\"specialty\":\"General\",\"yearsOfExperience\":3,"
                                + "\"department\":\"General\",\"consultationFee\":50.00}"))
                .andExpect(status().isCreated());
        return id;
    }

    private String userToken(String role) {
        return userToken(role, UUID.randomUUID());
    }

    private String userToken(String role, UUID uid) {
        return Jwts.builder()
                .subject("user-" + uid)
                .claim("uid", uid.toString())
                .claim("roles", List.of(role))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    private String serviceToken(String scope, String issuer, String audience, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(issuer)
                .issuer(issuer)
                .audience().add(audience).and()
                .claim(JwtClaims.TYPE, InternalServiceToken.TYPE_SERVICE)
                .claim(InternalServiceToken.CLAIM_SCOPE, scope)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(InternalServiceToken.signingKey(SECRET), Jwts.SIG.HS256)
                .compact();
    }
}
