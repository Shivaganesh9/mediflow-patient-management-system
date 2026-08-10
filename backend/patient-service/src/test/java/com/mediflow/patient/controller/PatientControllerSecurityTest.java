package com.mediflow.patient.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.secret=test-secret-key-for-patient-service-with-sufficient-length")
class PatientControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void patientEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/patients"))
                .andExpect(status().isUnauthorized());
    }
}
