package com.mediflow.doctor.controller;

import com.mediflow.doctor.dto.CreateDoctorRequest;
import com.mediflow.doctor.service.DoctorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.secret=test-secret-key-for-doctor-service-with-sufficient-length")
class DoctorControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DoctorService doctorService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doctorEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/doctors"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Calls the Spring-managed proxy directly, so the result cannot come from the URL matcher:
     * the {@code @PreAuthorize} on the service method must deny a role that is not allowed to
     * create doctor profiles.
     */
    @Test
    void methodSecurityDeniesDoctorCreationForANonProvisioningRole() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("nurse1", null,
                        List.of(new SimpleGrantedAuthority("ROLE_NURSE"))));

        assertThatThrownBy(() -> doctorService.createDoctor(new CreateDoctorRequest()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
