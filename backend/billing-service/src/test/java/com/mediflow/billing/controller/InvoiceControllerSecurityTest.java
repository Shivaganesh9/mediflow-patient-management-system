package com.mediflow.billing.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InvoiceControllerSecurityTest {

    private static final String SECRET = "test-secret-key-for-billing-service-with-sufficient-length";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void invoiceEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/invoices"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createInvoiceRejectsPatientRole() throws Exception {
        String token = Jwts.builder()
                .subject("patient1")
                .claim("roles", List.of("PATIENT"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        String payload = "{\"patientId\":\"" + UUID.randomUUID() + "\",\"currency\":\"USD\","
                + "\"items\":[{\"description\":\"Consultation\",\"quantity\":1,\"unitPrice\":100}]}";

        mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }
}
