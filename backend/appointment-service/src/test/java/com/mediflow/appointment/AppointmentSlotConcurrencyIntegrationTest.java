package com.mediflow.appointment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.appointment.schedule.DoctorScheduleValidator;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Real-PostgreSQL evidence that the database, not the application check, is the final authority on
 * doctor and patient slot booking.
 *
 * <p>Runs the actual Flyway migrations (V6 adds the exclusion constraints) against PostgreSQL 16 and
 * boots the service with {@code ddl-auto: validate}, so the mapping is proven against the migrated
 * schema too. Skipped automatically when Docker is unavailable.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AppointmentSlotConcurrencyIntegrationTest {

    static {
        // pgjdbc refuses to connect to PostgreSQL 16 when the JVM default zone is a legacy alias
        // (e.g. Asia/Calcutta), which is the default on Windows. Same workaround as the other
        // Testcontainers-backed tests in this repository.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    private static final String SECRET = "test-secret-key-for-appointment-service-with-sufficient-length";
    private static final String DOCTOR_SLOT_CONSTRAINT = "ex_appointments_doctor_slot";
    private static final String PATIENT_SLOT_CONSTRAINT = "ex_appointments_patient_slot";
    private static final LocalDateTime DAY = LocalDateTime.of(2031, 3, 4, 9, 0);

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

    @MockitoBean
    private DoctorScheduleValidator doctorScheduleValidator;

    @Test
    void migrationsCreatedTheSlotExclusionConstraint() {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from pg_extension where extname = 'btree_gist'", Integer.class))
                .isEqualTo(1);

        String definition = jdbcTemplate.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = ?",
                String.class, DOCTOR_SLOT_CONSTRAINT);

        assertThat(definition)
                .contains("EXCLUDE USING gist")
                .contains("doctor_id WITH =")
                .contains("tsrange(start_time, end_time")
                .contains("status");

        String patientDefinition = jdbcTemplate.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = ?",
                String.class, PATIENT_SLOT_CONSTRAINT);

        assertThat(patientDefinition)
                .contains("EXCLUDE USING gist")
                .contains("patient_id WITH =")
                .contains("tsrange(start_time, end_time");
    }

    @Test
    void simultaneousBookingsForTheSamePatientAcrossDifferentDoctorsCreateExactlyOneAppointment()
            throws Exception {
        UUID patientId = UUID.randomUUID();
        LocalDateTime start = DAY.plusDays(1);
        LocalDateTime end = start.plusMinutes(30);
        String token = token("admin1", UUID.randomUUID(), List.of("ADMIN"));
        int attempts = 5;

        // Each attempt uses a different doctor, so only the patient exclusion constraint can reject
        // the losers. This is the race the application-level patient check cannot close.
        List<Integer> statuses = runConcurrently(attempts,
                index -> book(token, payload(patientId, UUID.randomUUID(), start, end)));

        assertThat(statuses).as("booking outcomes").containsOnly(201, 409);
        assertThat(statuses.stream().filter(status -> status == 201).count())
                .as("exactly one concurrent booking may win for the same patient")
                .isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from appointments where patient_id = ? and status = 'SCHEDULED'",
                Integer.class, patientId))
                .isEqualTo(1);
    }

    @Test
    void simultaneousBookingsForTheSameSlotCreateExactlyOneAppointment() throws Exception {
        UUID doctorId = UUID.randomUUID();
        LocalDateTime start = DAY;
        LocalDateTime end = DAY.plusMinutes(30);
        String token = token("admin1", UUID.randomUUID(), List.of("ADMIN"));
        int attempts = 8;

        List<Integer> statuses = runConcurrently(attempts,
                index -> book(token, payload(UUID.randomUUID(), doctorId, start, end)));

        assertThat(statuses).as("booking outcomes").containsOnly(201, 409);
        assertThat(statuses.stream().filter(status -> status == 201).count())
                .as("exactly one concurrent booking may win")
                .isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from appointments where doctor_id = ? and status = 'SCHEDULED'",
                Integer.class, doctorId))
                .isEqualTo(1);

        // A rejected booking must not emit an event: the outbox row is written in the same
        // transaction and that transaction rolled back.
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from outbox_events where topic = 'appointment.created.v1' and payload like ?",
                Integer.class, "%" + doctorId + "%"))
                .isEqualTo(1);
    }

    @Test
    void databaseRejectsOverlapThatBypassesTheApplicationCheck() {
        UUID doctorId = UUID.randomUUID();
        insertRawAppointment(UUID.randomUUID(), doctorId, DAY, DAY.plusMinutes(30), "SCHEDULED");

        assertThatThrownBy(() ->
                insertRawAppointment(UUID.randomUUID(), doctorId, DAY.plusMinutes(10), DAY.plusMinutes(40), "CONFIRMED"))
                .satisfies(ex -> assertThat(sqlStateOf(ex))
                        .as("exclusion_violation proves PostgreSQL enforces the slot")
                        .isEqualTo("23P01"));
    }

    @Test
    void cancelledSlotCanBeRebooked() throws Exception {
        UUID doctorId = UUID.randomUUID();
        String token = token("admin1", UUID.randomUUID(), List.of("ADMIN"));
        LocalDateTime start = DAY;
        LocalDateTime end = DAY.plusHours(1);

        MvcResult created = mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(UUID.randomUUID(), doctorId, start, end)))
                .andReturn();
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String appointmentId = json(created).path("data").path("appointmentId").asText();

        assertThat(mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(UUID.randomUUID(), doctorId, start, end)))
                .andReturn().getResponse().getStatus())
                .as("the slot is reserved while the appointment is SCHEDULED")
                .isEqualTo(409);

        assertThat(mockMvc.perform(patch("/api/v1/appointments/{id}/cancel", appointmentId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus())
                .isEqualTo(200);

        assertThat(mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(UUID.randomUUID(), doctorId, start, end)))
                .andReturn().getResponse().getStatus())
                .as("cancelling releases the slot for rebooking")
                .isEqualTo(201);
    }

    @Test
    void backToBackAppointmentsSharingABoundaryDoNotConflict() throws Exception {
        UUID doctorId = UUID.randomUUID();
        String token = token("admin1", UUID.randomUUID(), List.of("ADMIN"));

        assertThat(book(token, payload(UUID.randomUUID(), doctorId, DAY, DAY.plusMinutes(30)))).isEqualTo(201);
        assertThat(book(token, payload(UUID.randomUUID(), doctorId, DAY.plusMinutes(30), DAY.plusHours(1))))
                .as("half-open ranges let the next appointment start when the previous one ends")
                .isEqualTo(201);
    }

    @Test
    void reschedulingIntoAnOccupiedSlotConflictsAndMovingAwayReleasesTheOriginalSlot() throws Exception {
        UUID doctorId = UUID.randomUUID();
        String token = token("admin1", UUID.randomUUID(), List.of("ADMIN"));

        String first = createAppointment(token, UUID.randomUUID(), doctorId, DAY, DAY.plusHours(1));
        String second = createAppointment(token, UUID.randomUUID(), doctorId, DAY.plusHours(2), DAY.plusHours(3));

        assertThat(reschedule(token, second, DAY.plusMinutes(15), DAY.plusMinutes(45)))
                .as("rescheduling onto an occupied slot is a conflict")
                .isEqualTo(409);

        assertThat(reschedule(token, second, DAY.plusHours(5), DAY.plusHours(6)))
                .as("rescheduling to a free slot succeeds")
                .isEqualTo(200);

        assertThat(book(token, payload(UUID.randomUUID(), doctorId, DAY.plusHours(2), DAY.plusHours(3))))
                .as("the vacated slot is bookable again")
                .isEqualTo(201);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from appointments where appointment_id = ?::uuid", Integer.class, first))
                .isEqualTo(1);
    }

    @Test
    void patientCanBookTheirOwnSlotButNotAnotherPatients() throws Exception {
        UUID patientUserId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        String patientToken = token("patient1", patientUserId, List.of("PATIENT"));

        assertThat(book(patientToken, payload(patientUserId, doctorId, DAY, DAY.plusMinutes(30)))).isEqualTo(201);
        assertThat(book(patientToken, payload(UUID.randomUUID(), doctorId, DAY.plusHours(1), DAY.plusHours(2))))
                .as("patients may not create appointments for someone else")
                .isEqualTo(403);
    }

    // ---------- helpers ----------

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

    private int book(String token, String payload) throws Exception {
        return mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn().getResponse().getStatus();
    }

    private String createAppointment(String token, UUID patientId, UUID doctorId,
                                     LocalDateTime start, LocalDateTime end) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(patientId, doctorId, start, end)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return json(result).path("data").path("appointmentId").asText();
    }

    private int reschedule(String token, String appointmentId, LocalDateTime start, LocalDateTime end)
            throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "startTime", start.toString(),
                "endTime", end.toString()));
        return mockMvc.perform(put("/api/v1/appointments/{id}", appointmentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    private void insertRawAppointment(UUID patientId, UUID doctorId, LocalDateTime start,
                                      LocalDateTime end, String status) {
        jdbcTemplate.update("""
                insert into appointments (id, appointment_id, patient_id, doctor_id, start_time, end_time,
                                          status, reason, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, now())
                """, UUID.randomUUID(), UUID.randomUUID(), patientId, doctorId,
                java.sql.Timestamp.valueOf(start), java.sql.Timestamp.valueOf(end), status, "Raw insert");
    }

    private String payload(UUID patientId, UUID doctorId, LocalDateTime start, LocalDateTime end) {
        return """
                {"patientId":"%s","doctorId":"%s","startTime":"%s","endTime":"%s","reason":"Checkup"}
                """.formatted(patientId, doctorId, start, end);
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
