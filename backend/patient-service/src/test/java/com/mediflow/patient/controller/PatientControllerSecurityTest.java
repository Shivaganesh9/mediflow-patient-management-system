package com.mediflow.patient.controller;

import com.mediflow.patient.dto.CreatePatientRequest;
import com.mediflow.patient.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.secret=test-secret-key-for-patient-service-with-sufficient-length")
class PatientControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PatientService patientService;

    @Test
    void patientEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/patients"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Calls the Spring-managed proxy directly, so the result cannot come from the URL matcher:
     * the {@code @PreAuthorize} on the service method must deny a role that is not allowed to
     * create patient profiles.
     */
    @Test
    @WithMockUser(roles = "NURSE")
    void methodSecurityDeniesPatientCreationForANonProvisioningRole() {
        assertThatThrownBy(() -> patientService.createPatient(new CreatePatientRequest()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
