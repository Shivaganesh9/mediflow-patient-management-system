package com.mediflow.appointment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.appointment.event.AppointmentEventPublisher;
import com.mediflow.appointment.schedule.DoctorScheduleValidator;
import com.mediflow.common.exception.ConflictException;
import com.mediflow.common.exception.ServiceUnavailableException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AppointmentEventPublisher eventPublisher;

    @MockitoBean
    private DoctorScheduleValidator doctorScheduleValidator;

    private UUID doctorId;
    private String doctorToken;

    @BeforeEach
    void setUp() {
        doctorId = UUID.randomUUID();
        doctorToken = token("doctor1", doctorId, List.of("DOCTOR"));
    }

    @Test
    void createAppointmentReturnsCreated() throws Exception {
        UUID patientId = UUID.randomUUID();
        String payload = appointmentPayload(patientId, doctorId, "2030-01-01T10:00:00", "2030-01-01T11:00:00");

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
    }

    @Test
    void createAppointmentRejectsEndBeforeStart() throws Exception {
        String payload = appointmentPayload(UUID.randomUUID(), doctorId, "2030-01-01T11:00:00", "2030-01-01T10:00:00");

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAppointmentRejectsMissingRequiredFields() throws Exception {
        String payload = "{\"reason\":\"Checkup\"}";

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAppointmentRejectsMalformedJsonWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void invalidPathVariableReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/not-a-uuid")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void healthAndReadinessProbesArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void metricsEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void createAppointmentByPatientUsesOwnPatientId() throws Exception {
        UUID patientId = UUID.randomUUID();
        String patientToken = token("patient1", patientId, List.of("PATIENT"));
        String payload = appointmentPayload(patientId, doctorId, "2030-01-01T10:00:00", "2030-01-01T11:00:00");

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.patientId").value(patientId.toString()));
    }

    @Test
    void createAppointmentByPatientRejectsOtherPatientId() throws Exception {
        String patientToken = token("patient1", UUID.randomUUID(), List.of("PATIENT"));
        String payload = appointmentPayload(UUID.randomUUID(), doctorId, "2030-01-01T10:00:00", "2030-01-01T11:00:00");

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAppointmentByDoctorRejectsOtherDoctorId() throws Exception {
        UUID otherDoctorId = UUID.randomUUID();
        String payload = appointmentPayload(UUID.randomUUID(), otherDoctorId, "2030-01-01T10:00:00", "2030-01-01T11:00:00");

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMissingAppointmentReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateMissingAppointmentReturnsNotFound() throws Exception {
        String payload = "{\"startTime\":\"2030-01-01T10:00:00\",\"endTime\":\"2030-01-01T11:00:00\"}";

        mockMvc.perform(put("/api/v1/appointments/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAppointmentByOwnerPatient() throws Exception {
        UUID patientId = UUID.randomUUID();
        String appointmentId = createAppointment(patientId, UUID.randomUUID(), adminToken());

        String patientToken = token("patient1", patientId, List.of("PATIENT"));
        mockMvc.perform(get("/api/v1/appointments/" + appointmentId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointmentId").value(appointmentId));
    }

    @Test
    void getAppointmentDeniedForOtherPatient() throws Exception {
        UUID patientId = UUID.randomUUID();
        String appointmentId = createAppointment(patientId, UUID.randomUUID(), adminToken());

        String otherPatientToken = token("patient2", UUID.randomUUID(), List.of("PATIENT"));
        mockMvc.perform(get("/api/v1/appointments/" + appointmentId)
                        .header("Authorization", "Bearer " + otherPatientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAppointmentDeniedForUnrelatedDoctor() throws Exception {
        UUID appointmentDoctorId = UUID.randomUUID();
        String appointmentId = createAppointment(UUID.randomUUID(), appointmentDoctorId, adminToken());

        String otherDoctorToken = token("doctor2", UUID.randomUUID(), List.of("DOCTOR"));
        mockMvc.perform(get("/api/v1/appointments/" + appointmentId)
                        .header("Authorization", "Bearer " + otherDoctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAppointmentByAdmin() throws Exception {
        String appointmentId = createAppointment(UUID.randomUUID(), UUID.randomUUID(), adminToken());

        mockMvc.perform(get("/api/v1/appointments/" + appointmentId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointmentId").value(appointmentId));
    }

    @Test
    void getAppointmentByStaff() throws Exception {
        String appointmentId = createAppointment(UUID.randomUUID(), UUID.randomUUID(), adminToken());

        String staffToken = token("nurse1", UUID.randomUUID(), List.of("NURSE"));
        mockMvc.perform(get("/api/v1/appointments/" + appointmentId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointmentId").value(appointmentId));
    }

    @Test
    void updateAppointmentDeniedForOtherDoctor() throws Exception {
        UUID appointmentDoctorId = UUID.randomUUID();
        String appointmentId = createAppointment(UUID.randomUUID(), appointmentDoctorId, adminToken());

        String otherDoctorToken = token("doctor2", UUID.randomUUID(), List.of("DOCTOR"));
        String payload = "{\"startTime\":\"2030-02-01T10:00:00\",\"endTime\":\"2030-02-01T11:00:00\"}";

        mockMvc.perform(put("/api/v1/appointments/" + appointmentId)
                        .header("Authorization", "Bearer " + otherDoctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelAppointmentDeniedForOtherPatient() throws Exception {
        UUID patientId = UUID.randomUUID();
        String appointmentId = createAppointment(patientId, UUID.randomUUID(), adminToken());

        String otherPatientToken = token("patient2", UUID.randomUUID(), List.of("PATIENT"));
        mockMvc.perform(patch("/api/v1/appointments/" + appointmentId + "/cancel")
                        .header("Authorization", "Bearer " + otherPatientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchAppointmentsScopedToPatient() throws Exception {
        UUID patientId = UUID.randomUUID();
        createAppointment(patientId, UUID.randomUUID(), adminToken());
        createAppointment(UUID.randomUUID(), UUID.randomUUID(), adminToken());
        String patientToken = token("patient1", patientId, List.of("PATIENT"));

        mockMvc.perform(get("/api/v1/appointments")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void searchAppointmentsForcesOwnScopeForPatient() throws Exception {
        String patientToken = token("patient1", UUID.randomUUID(), List.of("PATIENT"));

        mockMvc.perform(get("/api/v1/appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .param("patientId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchAppointmentsScopedToDoctor() throws Exception {
        UUID doctorA = UUID.randomUUID();
        UUID doctorB = UUID.randomUUID();
        createAppointment(UUID.randomUUID(), doctorA, adminToken());
        String doctorAToken = token("doctorA", doctorA, List.of("DOCTOR"));
        String doctorBToken = token("doctorB", doctorB, List.of("DOCTOR"));

        mockMvc.perform(get("/api/v1/appointments")
                        .header("Authorization", "Bearer " + doctorBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/v1/appointments")
                        .header("Authorization", "Bearer " + doctorAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void billingStaffAndSupportCanReadAnyAppointment() throws Exception {
        String appointmentId = createAppointment(UUID.randomUUID(), UUID.randomUUID(), adminToken());

        mockMvc.perform(get("/api/v1/appointments/" + appointmentId)
                        .header("Authorization", "Bearer "
                                + token("billing1", UUID.randomUUID(), List.of("BILLING_STAFF"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/appointments/" + appointmentId)
                        .header("Authorization", "Bearer "
                                + token("support1", UUID.randomUUID(), List.of("SUPPORT"))))
                .andExpect(status().isOk());
    }

    @Test
    void unknownAndLegacyRolesAreForbidden() throws Exception {
        String appointmentId = createAppointment(UUID.randomUUID(), UUID.randomUUID(), adminToken());

        for (String role : List.of("AUDITOR", "STAFF")) {
            mockMvc.perform(get("/api/v1/appointments/" + appointmentId)
                            .header("Authorization", "Bearer "
                                    + token(role.toLowerCase(), UUID.randomUUID(), List.of(role))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/appointments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsPastAppointmentSlot() throws Exception {
        String payload = appointmentPayload(UUID.randomUUID(), doctorId, "2020-01-01T10:00:00", "2020-01-01T11:00:00");

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBookingOutsideDoctorWorkingHours() throws Exception {
        doThrow(new ConflictException("The requested slot is outside the doctor's working hours (09:00-17:00)"))
                .when(doctorScheduleValidator).validateSlot(any(), any(), any());

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(UUID.randomUUID(), doctorId,
                                "2030-01-01T18:00:00", "2030-01-01T18:30:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("working hours")));
    }

    @Test
    void failsClosedWhenDoctorAvailabilityCannotBeChecked() throws Exception {
        doThrow(new ServiceUnavailableException("Doctor availability cannot be verified right now. Please retry shortly."))
                .when(doctorScheduleValidator).validateSlot(any(), any(), any());

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(UUID.randomUUID(), doctorId,
                                "2030-01-01T10:00:00", "2030-01-01T10:30:00")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void fullLifecycleTransitionsAndHistoryAreRecorded() throws Exception {
        UUID patientId = UUID.randomUUID();
        String appointmentId = createAppointment(patientId, doctorId, "2030-03-01T10:00:00", "2030-03-01T11:00:00");
        String patientToken = token("patient1", patientId, List.of("PATIENT"));

        mockMvc.perform(patch("/api/v1/appointments/{id}/confirm", appointmentId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        mockMvc.perform(patch("/api/v1/appointments/{id}/start", appointmentId)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        mockMvc.perform(patch("/api/v1/appointments/{id}/complete", appointmentId)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/appointments/{id}/history", appointmentId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].action").value("CREATED"))
                .andExpect(jsonPath("$.data[0].toStatus").value("SCHEDULED"))
                .andExpect(jsonPath("$.data[1].action").value("CONFIRMED"))
                .andExpect(jsonPath("$.data[1].fromStatus").value("SCHEDULED"))
                .andExpect(jsonPath("$.data[2].action").value("STARTED"))
                .andExpect(jsonPath("$.data[3].action").value("COMPLETED"));
    }

    @Test
    void invalidStatusTransitionIsRejectedWithConflict() throws Exception {
        String appointmentId = createAppointment(UUID.randomUUID(), doctorId, "2030-03-01T12:00:00", "2030-03-01T13:00:00");

        // SCHEDULED -> IN_PROGRESS is not allowed: the booking must be confirmed first.
        mockMvc.perform(patch("/api/v1/appointments/{id}/start", appointmentId)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("cannot be started in status SCHEDULED")));
    }

    @Test
    void patientsCannotMarkAnAppointmentAsANoShow() throws Exception {
        UUID patientId = UUID.randomUUID();
        String appointmentId = createAppointment(patientId, doctorId, "2030-03-01T14:00:00", "2030-03-01T15:00:00");
        String patientToken = token("patient1", patientId, List.of("PATIENT"));

        mockMvc.perform(patch("/api/v1/appointments/{id}/no-show", appointmentId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/appointments/{id}/no-show", appointmentId)
                        .header("Authorization", "Bearer " + token("nurse1", UUID.randomUUID(), List.of("NURSE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NO_SHOW"));
    }

    @Test
    void appointmentHistoryIsScopedToTheAppointmentOwner() throws Exception {
        UUID patientId = UUID.randomUUID();
        String appointmentId = createAppointment(patientId, doctorId, "2030-03-01T16:00:00", "2030-03-01T17:00:00");

        mockMvc.perform(get("/api/v1/appointments/{id}/history", appointmentId)
                        .header("Authorization", "Bearer " + token("patient2", UUID.randomUUID(), List.of("PATIENT"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/appointments/{id}/history", appointmentId)
                        .header("Authorization", "Bearer " + token("doctor2", UUID.randomUUID(), List.of("DOCTOR"))))
                .andExpect(status().isForbidden());
    }

    private String createAppointment(UUID patientId, UUID doctorId, String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(patientId, doctorId,
                                "2030-01-01T10:00:00", "2030-01-01T11:00:00")))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.at("/data/appointmentId").asText();
    }

    private String createAppointment(UUID patientId, UUID doctorId, String startTime, String endTime)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(patientId, doctorId, startTime, endTime)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.at("/data/appointmentId").asText();
    }

    private String appointmentPayload(UUID patientId, UUID doctorId, String startTime, String endTime) {
        return "{\"patientId\":\"" + patientId + "\","
                + "\"doctorId\":\"" + doctorId + "\","
                + "\"startTime\":\"" + startTime + "\",\"endTime\":\"" + endTime + "\","
                + "\"reason\":\"Checkup\"}";
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