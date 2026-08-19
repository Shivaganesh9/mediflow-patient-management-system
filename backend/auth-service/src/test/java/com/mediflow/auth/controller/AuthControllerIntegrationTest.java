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

    @Test
    void registerThenLoginShouldReturnTokens() throws Exception {
        String registerPayload = "{\"username\":\"loginuser\",\"email\":\"login@example.com\",\"password\":\"Test@12345\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(registerPayload))
                .andExpect(status().isCreated());

        Thread.sleep(1200);

        String loginPayload = "{\"usernameOrEmail\":\"loginuser\",\"password\":\"Test@12345\"}";
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginPayload))
                .andExpect(status().isOk());
    }
}
