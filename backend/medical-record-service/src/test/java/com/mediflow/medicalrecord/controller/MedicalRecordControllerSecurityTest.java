package com.mediflow.medicalrecord.controller;

import com.mediflow.medicalrecord.AbstractMedicalRecordIntegrationTest;
import com.mediflow.medicalrecord.dto.CreateMedicalRecordRequest;
import com.mediflow.medicalrecord.event.MedicalRecordEventPublisher;
import com.mediflow.medicalrecord.service.MedicalRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MedicalRecordControllerSecurityTest extends AbstractMedicalRecordIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MedicalRecordService medicalRecordService;

    @MockBean
    private MedicalRecordEventPublisher eventPublisher;

    @Test
    void medicalRecordEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/medical-records"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createMedicalRecordRejectsPatientRole() throws Exception {
        String patientToken = token("patient1", UUID.randomUUID(), List.of("PATIENT"));

        mockMvc.perform(post("/api/v1/medical-records")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    void createMedicalRecordRejectsNurseRole() throws Exception {
        String nurseToken = token("nurse1", UUID.randomUUID(), List.of("NURSE"));

        mockMvc.perform(post("/api/v1/medical-records")
                        .header("Authorization", "Bearer " + nurseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateMedicalRecordRejectsPatientRole() throws Exception {
        String patientToken = token("patient1", UUID.randomUUID(), List.of("PATIENT"));

        mockMvc.perform(put("/api/v1/medical-records/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Updated\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * Calls the Spring-managed proxy directly, so the result cannot come from the URL matcher:
     * the {@code @PreAuthorize} on the service method must deny a role that is not allowed to
     * write clinical records.
     */
    @Test
    @WithMockUser(roles = "NURSE")
    void methodSecurityDeniesMedicalRecordCreationForANonClinicalWriter() {
        assertThatThrownBy(() -> medicalRecordService.createMedicalRecord(new CreateMedicalRecordRequest()))
                .isInstanceOf(AccessDeniedException.class);
    }

    private String recordPayload() {
        return "{\"patientId\":\"" + UUID.randomUUID() + "\","
                + "\"encounterDate\":\"2026-08-10\","
                + "\"chiefComplaint\":\"Routine follow-up\","
                + "\"notes\":\"Patient reports improvement.\"}";
    }
}
