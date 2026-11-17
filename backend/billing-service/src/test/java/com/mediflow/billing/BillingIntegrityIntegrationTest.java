package com.mediflow.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Real-PostgreSQL evidence for billing business integrity: payment idempotency and the
 * one-active-invoice-per-appointment rule.
 *
 * <p>Runs the actual Flyway migrations (V6 idempotency table, V7 partial unique index) against
 * PostgreSQL 16 and boots the service with {@code ddl-auto: validate}. Skipped automatically when
 * Docker is unavailable.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class BillingIntegrityIntegrationTest {

    static {
        // pgjdbc refuses legacy JVM default zones on PostgreSQL 16 (see the other Testcontainers
        // tests in this repository).
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    private static final String SECRET = "test-secret-key-for-billing-service-with-sufficient-length";
    private static final String PAYMENT_TOPIC = "payment.confirmed.v1";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void retryWithTheSameKeyReturnsTheOriginalResultAndCreatesOnePayment() throws Exception {
        String invoiceId = createInvoice();
        String key = "pay-" + UUID.randomUUID();
        String body = "{\"amount\":247.50,\"method\":\"CARD\",\"reference\":\"auth-1\"}";

        MvcResult first = pay(invoiceId, token("billing-user", List.of("BILLING_STAFF")), key, body);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(first.getResponse().getHeader("Idempotent-Replay")).isNull();

        MvcResult retry = pay(invoiceId, token("billing-user", List.of("BILLING_STAFF")), key, body);
        assertThat(retry.getResponse().getStatus()).isEqualTo(201);
        assertThat(retry.getResponse().getHeader("Idempotent-Replay")).isEqualTo("true");
        assertThat(json(retry).at("/data/invoiceId").asText()).isEqualTo(invoiceId);
        assertThat(json(retry).at("/data/status").asText()).isEqualTo("PAID");
        assertThat(json(retry).at("/data/paidAmount").decimalValue()).isEqualByComparingTo("247.50");

        assertThat(paymentCount(invoiceId)).as("a retry must not create a second payment").isEqualTo(1);
        assertThat(paymentEventCount(invoiceId)).as("a retry must not publish a second event").isEqualTo(1);
    }

    @Test
    void concurrentRetriesWithTheSameKeyCreateOnePayment() throws Exception {
        String invoiceId = createInvoice();
        String key = "pay-concurrent-" + UUID.randomUUID();
        String body = "{\"amount\":100.00,\"method\":\"CARD\"}";
        String token = token("billing-user", List.of("BILLING_STAFF"));

        List<Integer> statuses = runConcurrently(6, index -> {
            MvcResult result = pay(invoiceId, token, key, body);
            return result.getResponse().getStatus();
        });

        assertThat(statuses).containsOnly(201);
        assertThat(paymentCount(invoiceId)).as("concurrent retries share one payment").isEqualTo(1);
        assertThat(paymentEventCount(invoiceId)).isEqualTo(1);
        assertThat(invoicePaidAmount(invoiceId)).isEqualByComparingTo("100.00");
    }

    @Test
    void reusingAKeyWithADifferentPayloadIsRejected() throws Exception {
        String invoiceId = createInvoice();
        String key = "pay-conflict-" + UUID.randomUUID();
        String token = token("billing-user", List.of("BILLING_STAFF"));

        assertThat(pay(invoiceId, token, key, "{\"amount\":100.00,\"method\":\"CARD\"}")
                .getResponse().getStatus()).isEqualTo(201);

        MvcResult conflicting = pay(invoiceId, token, key, "{\"amount\":50.00,\"method\":\"CARD\"}");

        assertThat(conflicting.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(conflicting).at("/success").asBoolean()).isFalse();
        assertThat(paymentCount(invoiceId)).isEqualTo(1);
        assertThat(invoicePaidAmount(invoiceId)).isEqualByComparingTo("100.00");
    }

    @Test
    void aFailedRequestLeavesNoIdempotencyRecordAndCanBeRetried() throws Exception {
        String invoiceId = createInvoice();
        String key = "pay-rollback-" + UUID.randomUUID();
        String token = token("billing-user", List.of("BILLING_STAFF"));

        // Overpayment: the domain rejects it, so the claim must roll back with the payment.
        assertThat(pay(invoiceId, token, key, "{\"amount\":9999.00,\"method\":\"CARD\"}")
                .getResponse().getStatus()).isEqualTo(409);
        assertThat(claimCount(key)).as("a rolled-back request must not leave a claim").isZero();
        assertThat(paymentCount(invoiceId)).isZero();

        // The same key is still usable because nothing was committed.
        assertThat(pay(invoiceId, token, key, "{\"amount\":247.50,\"method\":\"CARD\"}")
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(claimCount(key)).isEqualTo(1);
        assertThat(paymentCount(invoiceId)).isEqualTo(1);
    }

    @Test
    void differentCallersCanUseTheSameKey() throws Exception {
        String key = "pay-shared-" + UUID.randomUUID();
        String invoiceA = createInvoice();
        String invoiceB = createInvoice();

        assertThat(pay(invoiceA, token("billing-a", List.of("BILLING_STAFF")), key,
                "{\"amount\":247.50,\"method\":\"CARD\"}").getResponse().getStatus())
                .isEqualTo(201);
        assertThat(pay(invoiceB, token("billing-b", List.of("BILLING_STAFF")), key,
                "{\"amount\":247.50,\"method\":\"CARD\"}").getResponse().getStatus())
                .as("the key is scoped to the caller, not global")
                .isEqualTo(201);

        assertThat(paymentCount(invoiceA)).isEqualTo(1);
        assertThat(paymentCount(invoiceB)).isEqualTo(1);
    }

    @Test
    void theKeyIsUniquePerCallerInTheDatabase() throws Exception {
        UUID invoiceId = UUID.fromString(createInvoice());
        String key = "db-scope-" + UUID.randomUUID();

        insertClaim(UUID.randomUUID(), key, "billing-user", invoiceId);

        assertThatThrownBy(() -> insertClaim(UUID.randomUUID(), key, "billing-user", invoiceId))
                .satisfies(ex -> assertThat(sqlStateOf(ex)).isEqualTo("23505"));
    }

    @Test
    void patientsCannotRecordPayments() throws Exception {
        String invoiceId = createInvoice();
        UUID patientId = UUID.randomUUID();

        assertThat(pay(invoiceId, token("patient1", patientId, List.of("PATIENT")),
                "pay-patient-" + UUID.randomUUID(), "{\"amount\":10.00,\"method\":\"CARD\"}")
                .getResponse().getStatus())
                .isEqualTo(403);
    }

    @Test
    void aSecondActiveInvoiceForTheSameAppointmentIsRejected() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        String token = token("billing-user", List.of("BILLING_STAFF"));

        assertThat(createInvoice(token, UUID.randomUUID(), appointmentId).getResponse().getStatus())
                .isEqualTo(201);
        assertThat(createInvoice(token, UUID.randomUUID(), appointmentId).getResponse().getStatus())
                .as("an appointment may not be billed twice")
                .isEqualTo(409);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from invoices where appointment_id = ?::uuid and status <> 'CANCELLED'",
                Integer.class, appointmentId.toString()))
                .isEqualTo(1);
    }

    // ---------- helpers ----------

    private void insertClaim(UUID id, String key, String requester, UUID invoiceId) {
        jdbcTemplate.update("""
                insert into payment_idempotency (id, idempotency_key, requester, invoice_id,
                                                request_fingerprint, created_at)
                values (?, ?, ?, ?, ?, now())
                """, id, key, requester, invoiceId, "0".repeat(64));
    }

    private String createInvoice() throws Exception {
        MvcResult result = createInvoice(token("billing-user", List.of("BILLING_STAFF")),
                UUID.randomUUID(), null);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return json(result).at("/data/invoiceId").asText();
    }

    private MvcResult createInvoice(String token, UUID patientId, UUID appointmentId) throws Exception {
        String body = "{\"patientId\":\"" + patientId + "\",\"currency\":\"USD\","
                + (appointmentId == null ? "" : "\"appointmentId\":\"" + appointmentId + "\",")
                + "\"taxRate\":10,\"discount\":25,\"items\":["
                + "{\"itemType\":\"CONSULTATION\",\"description\":\"Consultation\",\"quantity\":2,\"unitPrice\":100},"
                + "{\"itemType\":\"LAB_TEST\",\"description\":\"Blood test\",\"quantity\":1,\"unitPrice\":50}]}";
        return mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private MvcResult pay(String invoiceId, String token, String key, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/invoices/{id}/payments", invoiceId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private int paymentCount(String invoiceId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from payments where invoice_id = "
                        + "(select id from invoices where invoice_id = ?::uuid)", Integer.class, invoiceId);
    }

    private int paymentEventCount(String invoiceId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from outbox_events where topic = ? and payload like ?",
                Integer.class, PAYMENT_TOPIC, "%" + invoiceId + "%");
    }

    private java.math.BigDecimal invoicePaidAmount(String invoiceId) {
        return jdbcTemplate.queryForObject(
                "select paid_amount from invoices where invoice_id = ?::uuid",
                java.math.BigDecimal.class, invoiceId);
    }

    private int claimCount(String key) {
        return jdbcTemplate.queryForObject(
                "select count(*) from payment_idempotency where idempotency_key = ?", Integer.class, key);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String sqlStateOf(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }
        return null;
    }

    private List<Integer> runConcurrently(int attempts, ThrowingIntFunction task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger index = new AtomicInteger();
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return task.apply(index.getAndIncrement());
                }));
            }
            startGate.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private String token(String subject, List<String> roles) {
        return token(subject, UUID.randomUUID(), roles);
    }

    private String token(String subject, UUID uid, List<String> roles) {
        JwtBuilder builder = Jwts.builder()
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

    @FunctionalInterface
    private interface ThrowingIntFunction {
        int apply(int index) throws Exception;
    }
}
