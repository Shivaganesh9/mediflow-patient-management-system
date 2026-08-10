package com.mediflow.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerEndpointShouldAcceptValidPayload() throws Exception {
        String payload = "{\"username\":\"testuser\",\"email\":\"test@example.com\",\"password\":\"password123\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated());
    }

    @Test
    void currentUserEndpointShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
