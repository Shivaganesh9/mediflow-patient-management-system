package com.mediflow.medicalrecord.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.medicalrecord.AbstractMedicalRecordIntegrationTest;
import com.mediflow.medicalrecord.event.MedicalRecordEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MedicalRecordControllerIntegrationTest extends AbstractMedicalRecordIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MedicalRecordEventPublisher eventPublisher;

    @Test
    void createMedicalRecordByDoctorReturnsCreated() throws Exception {
        UUID doctorId = UUID.randomUUID();
        String token = token("doctor1", doctorId, List.of("DOCTOR"));
        UUID patientId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/medical-records")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordPayload(patientId, "FOLLOW_UP")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.patientId").value(patientId.toString()))
                .andExpect(jsonPath("$.data.doctorId").value(doctorId.toString()))
                .andExpect(jsonPath("$.data.recordType").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.data.chiefComplaint").value("Routine follow-up"))
                .andExpect(jsonPath("$.data.diagnosis").value("Hypertension stable"))
                .andExpect(jsonPath("$.data.recordId").isNotEmpty());
    }

    @Test
    void createMedicalRecordDefaultsRecordTypeAndDoctor() throws Exception {
        UUID doctorId = UUID.randomUUID();
        String token = token("doctor1", doctorId, List.of("DOCTOR"));

        mockMvc.perform(post("/api/v1/medical-records")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordPayload(UUID.randomUUID(), null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recordType").value("CONSULTATION"))
                .andExpect(jsonPath("$.data.doctorId").value(doctorId.toString()));
    }

    @Test
    void createMedicalRecordRejectsMissingRequiredFields() throws Exception {
        String token = token("doctor1", UUID.randomUUID(), List.of("DOCTOR"));

        mockMvc.perform(post("/api/v1/medical-records")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chiefComplaint\":\"Cough\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createMedicalRecordByAdminUsesProvidedDoctorId() throws Exception {
        UUID providedDoctorId = UUID.randomUUID();
        String token = token("admin", null, List.of("ADMIN"));

        String payload = "{\"patientId\":\"" + UUID.randomUUID() + "\","
                + "\"doctorId\":\"" + providedDoctorId + "\","
                + "\"encounterDate\":\"2026-08-10\","
                + "\"chiefComplaint\":\"Cough\","
                + "\"notes\":\"Prescribed antibiotics.\"}";

        mockMvc.perform(post("/api/v1/medical-records")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.doctorId").value(providedDoctorId.toString()));
    }

    @Test
    void getMedicalRecordByOwnerPatient() throws Exception {
        UUID patientId = UUID.randomUUID();
        String recordId = createRecord(patientId, "CONSULTATION",
                token("doctor1", UUID.randomUUID(), List.of("DOCTOR")));
        String patientToken = token("patient1", patientId, List.of("PATIENT"));

        mockMvc.perform(get("/api/v1/medical-records/" + recordId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordId").value(recordId));
    }

    @Test
    void getMedicalRecordDeniedForOtherPatient() throws Exception {
        UUID patientId = UUID.randomUUID();
        String recordId = createRecord(patientId, "CONSULTATION",
                token("doctor1", UUID.randomUUID(), List.of("DOCTOR")));
        String otherPatientToken = token("patient2", UUID.randomUUID(), List.of("PATIENT"));

        mockMvc.perform(get("/api/v1/medical-records/" + recordId)
                        .header("Authorization", "Bearer " + otherPatientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMedicalRecordDeniedForUnrelatedDoctor() throws Exception {
        UUID doctorId = UUID.randomUUID();
        String recordId = createRecord(UUID.randomUUID(), "CONSULTATION",
                token("doctor1", doctorId, List.of("DOCTOR")));
        String otherDoctorToken = token("doctor2", UUID.randomUUID(), List.of("DOCTOR"));

        mockMvc.perform(get("/api/v1/medical-records/" + recordId)
                        .header("Authorization", "Bearer " + otherDoctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMedicalRecordDeniedForDoctorMatchingCreatedByOnly() throws Exception {
        UUID recordOwnerDoctorId = UUID.randomUUID();
        String recordId = createRecord(UUID.randomUUID(), "CONSULTATION",
                token("doctor1", recordOwnerDoctorId, List.of("DOCTOR")));
        String doctorWithSameUsernameDifferentUid = token("doctor1", UUID.randomUUID(), List.of("DOCTOR"));

        mockMvc.perform(get("/api/v1/medical-records/" + recordId)
                        .header("Authorization", "Bearer " + doctorWithSameUsernameDifferentUid))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMedicalRecordByAdmin() throws Exception {
        String recordId = createRecord(UUID.randomUUID(), "CONSULTATION",
                token("doctor1", UUID.randomUUID(), List.of("DOCTOR")));
        String adminToken = token("admin", null, List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/medical-records/" + recordId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordId").value(recordId));
    }

    @Test
    void getMissingMedicalRecordReturnsNotFound() throws Exception {
        String token = token("admin", null, List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/medical-records/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchMedicalRecordsScopedToPatient() throws Exception {
        UUID patientId = UUID.randomUUID();
        String doctorToken = token("doctor1", UUID.randomUUID(), List.of("DOCTOR"));
        createRecord(patientId, "CONSULTATION", doctorToken);
        createRecord(patientId, "FOLLOW_UP", doctorToken);
        String patientToken = token("patient1", patientId, List.of("PATIENT"));

        mockMvc.perform(get("/api/v1/medical-records")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void searchMedicalRecordsForcesOwnScopeForPatient() throws Exception {
        UUID otherPatientId = UUID.randomUUID();
        String patientToken = token("patient1", UUID.randomUUID(), List.of("PATIENT"));

        mockMvc.perform(get("/api/v1/medical-records")
                        .header("Authorization", "Bearer " + patientToken)
                        .param("patientId", otherPatientId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchMedicalRecordsScopedToDoctor() throws Exception {
        UUID doctorA = UUID.randomUUID();
        String doctorAToken = token("doctorA", doctorA, List.of("DOCTOR"));
        String doctorBToken = token("doctorB", UUID.randomUUID(), List.of("DOCTOR"));

        createRecord(UUID.randomUUID(), "CONSULTATION", doctorAToken);

        mockMvc.perform(get("/api/v1/medical-records")
                        .header("Authorization", "Bearer " + doctorBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/v1/medical-records")
                        .header("Authorization", "Bearer " + doctorAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void searchMedicalRecordsFiltersByRecordType() throws Exception {
        UUID patientId = UUID.randomUUID();
        String adminToken = token("admin", null, List.of("ADMIN"));
        createRecord(patientId, "CONSULTATION", adminToken);
        createRecord(patientId, "FOLLOW_UP", adminToken);

        mockMvc.perform(get("/api/v1/medical-records")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("recordType", "FOLLOW_UP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void listMedicalRecordsDeniedForStaffRole() throws Exception {
        String nurseToken = token("nurse1", UUID.randomUUID(), List.of("NURSE"));

        mockMvc.perform(get("/api/v1/medical-records")
                        .header("Authorization", "Bearer " + nurseToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateMedicalRecordByTreatingDoctor() throws Exception {
        UUID doctorId = UUID.randomUUID();
        String doctorToken = token("doctor1", doctorId, List.of("DOCTOR"));
        String recordId = createRecord(UUID.randomUUID(), "CONSULTATION", doctorToken);

        String payload = "{\"diagnosis\":\"Updated diagnosis\",\"notes\":\"Updated notes\"}";

        mockMvc.perform(put("/api/v1/medical-records/" + recordId)
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diagnosis").value("Updated diagnosis"))
                .andExpect(jsonPath("$.data.notes").value("Updated notes"));
    }

    @Test
    void updateMedicalRecordDeniedForOtherDoctor() throws Exception {
        String recordId = createRecord(UUID.randomUUID(), "CONSULTATION",
                token("doctor1", UUID.randomUUID(), List.of("DOCTOR")));
        String otherDoctorToken = token("doctor2", UUID.randomUUID(), List.of("DOCTOR"));

        mockMvc.perform(put("/api/v1/medical-records/" + recordId)
                        .header("Authorization", "Bearer " + otherDoctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Updated\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateMissingRecordReturnsNotFound() throws Exception {
        String doctorToken = token("doctor1", UUID.randomUUID(), List.of("DOCTOR"));

        mockMvc.perform(put("/api/v1/medical-records/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Updated\"}"))
                .andExpect(status().isNotFound());
    }

    private String createRecord(UUID patientId, String recordType, String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/medical-records")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordPayload(patientId, recordType)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.at("/data/recordId").asText();
    }

    private String recordPayload(UUID patientId, String recordType) {
        String type = recordType == null ? "" : ",\"recordType\":\"" + recordType + "\"";
        return "{\"patientId\":\"" + patientId + "\","
                + "\"appointmentId\":\"" + UUID.randomUUID() + "\","
                + "\"encounterDate\":\"2026-08-10\","
                + "\"chiefComplaint\":\"Routine follow-up\","
                + "\"diagnosis\":\"Hypertension stable\","
                + "\"treatmentPlan\":\"Continue medication\","
                + "\"notes\":\"Patient reports improvement.\""
                + type + "}";
    }
}
