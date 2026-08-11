package com.mediflow.billing.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.event.BillingEventPublisher;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InvoiceControllerIntegrationTest {

    private static final String SECRET = "test-secret-key-for-billing-service-with-sufficient-length";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BillingEventPublisher eventPublisher;

    private String billingToken() {
        return token(List.of("BILLING_STAFF"));
    }

    @Test
    void createInvoiceReturnsCreatedWithPendingStatus() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + billingToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoicePayload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.subtotal").value(250.00))
                .andExpect(jsonPath("$.data.totalAmount").value(247.50))
                .andExpect(jsonPath("$.data.invoiceNumber").isNotEmpty());
    }

    @Test
    void createInvoiceRejectsEmptyItems() throws Exception {
        String payload = "{\"patientId\":\"" + UUID.randomUUID() + "\",\"currency\":\"USD\",\"items\":[]}";

        mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + billingToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createInvoiceRejectsMissingRequiredFields() throws Exception {
        String payload = "{\"currency\":\"USD\"}";

        mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + billingToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createInvoiceRejectsNegativeQuantity() throws Exception {
        String payload = "{\"patientId\":\"" + UUID.randomUUID() + "\",\"currency\":\"USD\",\"items\":["
                + "{\"description\":\"Consultation\",\"quantity\":-1,\"unitPrice\":100}]}";

        mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + billingToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getInvoiceReturnsCreatedInvoice() throws Exception {
        String invoiceId = createInvoice();

        mockMvc.perform(get("/api/v1/invoices/" + invoiceId)
                        .header("Authorization", "Bearer " + billingToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceId").value(invoiceId));
    }

    @Test
    void getMissingInvoiceReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/invoices/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + billingToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listInvoicesReturnsPage() throws Exception {
        createInvoice();

        mockMvc.perform(get("/api/v1/invoices")
                        .header("Authorization", "Bearer " + billingToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void updateInvoiceModifiesTotalsWhilePending() throws Exception {
        String invoiceId = createInvoice();
        String payload = "{\"taxRate\":5,\"discount\":10,\"items\":["
                + "{\"description\":\"Follow-up\",\"quantity\":1,\"unitPrice\":200}]}";

        mockMvc.perform(put("/api/v1/invoices/" + invoiceId)
                        .header("Authorization", "Bearer " + billingToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.totalAmount").value(199.50));
    }

    @Test
    void recordPaymentMarksInvoicePaid() throws Exception {
        String invoiceId = createInvoice();
        String payload = "{\"amount\":247.50,\"method\":\"CARD\"}";

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
                        .header("Authorization", "Bearer " + billingToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paidAmount").value(247.50));
    }

    @Test
    void recordPaymentRejectsAmountAboveBalance() throws Exception {
        String invoiceId = createInvoice();
        String payload = "{\"amount\":9999,\"method\":\"CARD\"}";

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
                        .header("Authorization", "Bearer " + billingToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelInvoiceThenRejectSecondCancel() throws Exception {
        String invoiceId = createInvoice();

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/cancel")
                        .header("Authorization", "Bearer " + billingToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/cancel")
                        .header("Authorization", "Bearer " + billingToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paymentOnCancelledInvoiceIsRejected() throws Exception {
        String invoiceId = createInvoice();

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/cancel")
                .header("Authorization", "Bearer " + billingToken()))
                .andExpect(status().isOk());

        String payload = "{\"amount\":50,\"method\":\"CARD\"}";
        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
                        .header("Authorization", "Bearer " + billingToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refundPaymentReopensPartiallyPaidInvoice() throws Exception {
        String invoiceId = createInvoice();

        MvcResult result = mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
                        .header("Authorization", "Bearer " + billingToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":247.50,\"method\":\"CARD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String paymentId = body.at("/data/payments/0/paymentId").asText();

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refund")
                        .header("Authorization", "Bearer " + billingToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.paidAmount").value(0.0));
    }

    private String createInvoice() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + billingToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoicePayload()))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.at("/data/invoiceId").asText();
    }

    private String invoicePayload() {
        return "{\"patientId\":\"" + UUID.randomUUID() + "\",\"currency\":\"USD\","
                + "\"taxRate\":10,\"discount\":25,\"items\":["
                + "{\"itemType\":\"CONSULTATION\",\"description\":\"Consultation\",\"quantity\":2,\"unitPrice\":100},"
                + "{\"itemType\":\"LAB_TEST\",\"description\":\"Blood test\",\"quantity\":1,\"unitPrice\":50}]}";
    }

    private String token(List<String> roles) {
        return Jwts.builder()
                .subject("billing-user")
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }
}
