package com.mediflow.doctor.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.security.InternalServiceToken;
import com.mediflow.common.security.JwtClaims;
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
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.secret=test-secret-key-for-doctor-service-with-sufficient-length")
class DoctorScheduleControllerIntegrationTest {

    private static final String SECRET = "test-secret-key-for-doctor-service-with-sufficient-length";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IdentityVerifier identityVerifier;

    @Test
    void doctorManagesWorkingHoursAndTheInternalValidationUsesThem() throws Exception {
        UUID doctorId = createOwnProfile();
        String token = doctorToken(doctorId);
        LocalDate monday = next(DayOfWeek.MONDAY);

        MvcResult created = mockMvc.perform(post("/api/v1/doctors/{id}/availability", doctorId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"endTime\":\"17:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();
        String availabilityId = json(created).path("data").path("availabilityId").asText();

        // Overlapping window on the same day is a conflict, not a validation error.
        mockMvc.perform(post("/api/v1/doctors/{id}/availability", doctorId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"16:00\",\"endTime\":\"18:00\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/doctors/{id}/availability/{availabilityId}", doctorId, availabilityId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"08:00\",\"endTime\":\"17:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startTime").value("08:00:00"));

        mockMvc.perform(get("/internal/doctors/{id}/schedule/validation", doctorId)
                        .header("Authorization", "Bearer " + serviceToken())
                        .param("startTime", monday.atTime(10, 0).toString())
                        .param("endTime", monday.atTime(10, 30).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));

        mockMvc.perform(get("/internal/doctors/{id}/schedule/validation", doctorId)
                        .header("Authorization", "Bearer " + serviceToken())
                        .param("startTime", monday.atTime(18, 0).toString())
                        .param("endTime", monday.atTime(18, 30).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false));

        mockMvc.perform(delete("/api/v1/doctors/{id}/availability/{availabilityId}", doctorId, availabilityId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(get("/api/v1/doctors/{id}/availability", doctorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void doctorCreatesUpdatesAndCancelsLeaveAndBookingsAreRejectedDuringLeave() throws Exception {
        UUID doctorId = createOwnProfile();
        String token = doctorToken(doctorId);
        LocalDate monday = next(DayOfWeek.MONDAY);

        mockMvc.perform(post("/api/v1/doctors/{id}/availability", doctorId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"endTime\":\"17:00\"}"))
                .andExpect(status().isCreated());

        MvcResult created = mockMvc.perform(post("/api/v1/doctors/{id}/leaves", doctorId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"" + monday + "\",\"endDate\":\"" + monday + "\",\"reason\":\"Conference\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String leaveId = json(created).path("data").path("leaveId").asText();

        mockMvc.perform(post("/api/v1/doctors/{id}/leaves", doctorId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"" + monday + "\",\"endDate\":\"" + monday.plusDays(1) + "\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/internal/doctors/{id}/schedule/validation", doctorId)
                        .header("Authorization", "Bearer " + serviceToken())
                        .param("startTime", monday.atTime(10, 0).toString())
                        .param("endTime", monday.atTime(10, 30).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.reason").value(org.hamcrest.Matchers.containsString("on leave")));

        mockMvc.perform(put("/api/v1/doctors/{id}/leaves/{leaveId}", doctorId, leaveId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"" + monday.plusDays(1) + "\",\"endDate\":\"" + monday.plusDays(2) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value(monday.plusDays(1).toString()));

        mockMvc.perform(delete("/api/v1/doctors/{id}/leaves/{leaveId}", doctorId, leaveId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/doctors/{id}/leaves", doctorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void doctorCannotManageAnotherDoctorsSchedule() throws Exception {
        UUID otherDoctorId = createOwnProfile();
        UUID selfId = createOwnProfile();

        mockMvc.perform(post("/api/v1/doctors/{id}/availability", otherDoctorId)
                        .header("Authorization", "Bearer " + doctorToken(selfId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"endTime\":\"17:00\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotManageADoctorsSchedule() throws Exception {
        UUID doctorId = createOwnProfile();

        mockMvc.perform(post("/api/v1/doctors/{id}/availability", doctorId)
                        .header("Authorization", "Bearer " + token("nurse1", UUID.randomUUID(), List.of("NURSE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"endTime\":\"17:00\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientsCannotManageADoctorsScheduleButCanReadIt() throws Exception {
        UUID doctorId = createOwnProfile();
        String patientToken = token("patient1", UUID.randomUUID(), List.of("PATIENT"));

        mockMvc.perform(post("/api/v1/doctors/{id}/availability", doctorId)
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"endTime\":\"17:00\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/doctors/{id}/availability", doctorId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());
    }

    private UUID createOwnProfile() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/doctors")
                        .header("Authorization", "Bearer " + doctorToken(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jane\",\"lastName\":\"Doe\","
                                + "\"email\":\"schedule-" + id + "@example.com\",\"phone\":\"555-0100\","
                                + "\"specialty\":\"Cardiology\",\"yearsOfExperience\":12,"
                                + "\"department\":\"Cardiology\",\"consultationFee\":150.00}"))
                .andExpect(status().isCreated());
        return id;
    }

    private LocalDate next(DayOfWeek day) {
        LocalDate date = LocalDate.now().plusDays(1);
        while (date.getDayOfWeek() != day) {
            date = date.plusDays(1);
        }
        return date;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String doctorToken(UUID doctorId) {
        return token("doctor-" + doctorId, doctorId, List.of("DOCTOR"));
    }

    /**
     * Mints the service credential appointment-service would send. User tokens are signed with the
     * raw {@code jwt.secret}; service tokens use the derived key and are therefore not
     * interchangeable.
     */
    private String serviceToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(InternalServiceToken.ISSUER_APPOINTMENT_SERVICE)
                .issuer(InternalServiceToken.ISSUER_APPOINTMENT_SERVICE)
                .audience().add(InternalServiceToken.AUDIENCE_DOCTOR_SERVICE).and()
                .claim(JwtClaims.TYPE, InternalServiceToken.TYPE_SERVICE)
                .claim(InternalServiceToken.CLAIM_SCOPE, InternalServiceToken.SCOPE_DOCTOR_SCHEDULE_VALIDATE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(30)))
                .signWith(InternalServiceToken.signingKey(SECRET), Jwts.SIG.HS256)
                .compact();
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
