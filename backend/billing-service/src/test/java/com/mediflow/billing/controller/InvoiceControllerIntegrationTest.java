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
    void listInvoicesScopedToPatient() throws Exception {
        UUID patientA = UUID.randomUUID();
        UUID patientB = UUID.randomUUID();
        createInvoice(patientA, billingToken());
        createInvoice(patientB, billingToken());

        String patientAToken = token("patient1", patientA, List.of("PATIENT"));
        mockMvc.perform(get("/api/v1/invoices")
                        .header("Authorization", "Bearer " + patientAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].patientId").value(patientA.toString()));

        String patientBToken = token("patient2", patientB, List.of("PATIENT"));
        mockMvc.perform(get("/api/v1/invoices")
                        .header("Authorization", "Bearer " + patientBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].patientId").value(patientB.toString()));
    }

    @Test
    void patientListForcesOwnScopeWhenOtherPatientRequested() throws Exception {
        UUID patientA = UUID.randomUUID();
        UUID patientB = UUID.randomUUID();
        createInvoice(patientA, billingToken());

        String patientAToken = token("patient1", patientA, List.of("PATIENT"));
        mockMvc.perform(get("/api/v1/invoices")
                        .header("Authorization", "Bearer " + patientAToken)
                        .param("patientId", patientB.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getInvoiceByOwnerPatient() throws Exception {
        UUID patientId = UUID.randomUUID();
        String invoiceId = createInvoice(patientId, billingToken());

        String patientToken = token("patient1", patientId, List.of("PATIENT"));
        mockMvc.perform(get("/api/v1/invoices/" + invoiceId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceId").value(invoiceId));
    }

    @Test
    void getInvoiceDeniedForOtherPatient() throws Exception {
        UUID patientId = UUID.randomUUID();
        String invoiceId = createInvoice(patientId, billingToken());

        String otherPatientToken = token("patient2", UUID.randomUUID(), List.of("PATIENT"));
        mockMvc.perform(get("/api/v1/invoices/" + invoiceId)
                        .header("Authorization", "Bearer " + otherPatientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPaymentsDeniedForOtherPatient() throws Exception {
        UUID patientId = UUID.randomUUID();
        String invoiceId = createInvoice(patientId, billingToken());

        String otherPatientToken = token("patient2", UUID.randomUUID(), List.of("PATIENT"));
        mockMvc.perform(get("/api/v1/invoices/" + invoiceId + "/payments")
                        .header("Authorization", "Bearer " + otherPatientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getInvoiceByAdmin() throws Exception {
        String invoiceId = createInvoice();

        mockMvc.perform(get("/api/v1/invoices/" + invoiceId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceId").value(invoiceId));
    }

    @Test
    void listInvoicesReturnsPage() throws Exception {
        UUID patientId = UUID.randomUUID();
        createInvoice(patientId, billingToken());

        mockMvc.perform(get("/api/v1/invoices")
                        .param("patientId", patientId.toString())
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
        return createInvoice(UUID.randomUUID(), billingToken());
    }

    private String createInvoice(UUID patientId, String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoicePayload(patientId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.at("/data/invoiceId").asText();
    }

    private String invoicePayload() {
        return invoicePayload(UUID.randomUUID());
    }

    private String invoicePayload(UUID patientId) {
        return "{\"patientId\":\"" + patientId + "\",\"currency\":\"USD\","
                + "\"taxRate\":10,\"discount\":25,\"items\":["
                + "{\"itemType\":\"CONSULTATION\",\"description\":\"Consultation\",\"quantity\":2,\"unitPrice\":100},"
                + "{\"itemType\":\"LAB_TEST\",\"description\":\"Blood test\",\"quantity\":1,\"unitPrice\":50}]}";
    }

    private String token(List<String> roles) {
        return token("billing-user", null, roles);
    }

    private String adminToken() {
        return token("admin", null, List.of("ADMIN"));
    }

    private String token(String subject, UUID uid, List<String> roles) {
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
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
