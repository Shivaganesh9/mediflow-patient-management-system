package com.mediflow.analytics.event;

import com.mediflow.analytics.AbstractAnalyticsIntegrationTest;
import com.mediflow.analytics.service.AnalyticsService;
import com.mediflow.appointment.event.AppointmentEvent;
import com.mediflow.billing.event.BillingEvent;
import com.mediflow.billing.entity.InvoiceStatus;
import com.mediflow.billing.entity.PaymentMethod;
import com.mediflow.billing.entity.PaymentStatus;
import com.mediflow.medicalrecord.entity.MedicalRecordType;
import com.mediflow.medicalrecord.event.MedicalRecordEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-PostgreSQL evidence that analytics event processing is idempotent.
 *
 * <p>Kafka delivers at least once, so the same event can arrive again after a restart or a retry.
 * The {@code processed_events} ledger plus the fact-table constraints must keep one fact row per
 * aggregate and one ledger entry per event. Runs the real Flyway migrations against PostgreSQL 16
 * and is skipped automatically when Docker is unavailable.</p>
 */
@SpringBootTest
class AnalyticsEventIdempotencyIntegrationTest extends AbstractAnalyticsIntegrationTest {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void redeliveredAppointmentEventCreatesOneFactAndOneLedgerEntry() {
        AppointmentEvent event = appointmentEvent(UUID.randomUUID(), UUID.randomUUID());

        analyticsService.processAppointmentEvent(event);
        analyticsService.processAppointmentEvent(event);
        analyticsService.processAppointmentEvent(event);

        assertThat(count("select count(*) from appointment_facts where appointment_id = ?",
                event.getPayload().getAppointmentId()))
                .as("a redelivered event must not duplicate the fact row")
                .isEqualTo(1);
        assertThat(count("select count(*) from processed_events where event_id = ?", event.getEventId()))
                .as("the event is recorded once in the idempotency ledger")
                .isEqualTo(1);
    }

    @Test
    void concurrentRedeliveryConvergesOnOneFactRow() throws Exception {
        AppointmentEvent event = appointmentEvent(UUID.randomUUID(), UUID.randomUUID());

        // A losing thread may see a retryable integrity error; the broker redelivers and the ledger
        // pre-check then skips it. No duplicate fact row is ever allowed.
        runConcurrently(6, () -> analyticsService.processAppointmentEvent(event));

        assertThat(count("select count(*) from appointment_facts where appointment_id = ?",
                event.getPayload().getAppointmentId()))
                .isEqualTo(1);
        assertThat(count("select count(*) from processed_events where event_id = ?", event.getEventId()))
                .isEqualTo(1);
    }

    @Test
    void redeliveredPaymentEventCreatesOnePaymentFact() {
        BillingEvent event = paymentConfirmedEvent(UUID.randomUUID(), UUID.randomUUID());

        analyticsService.processBillingEvent(event);
        analyticsService.processBillingEvent(event);

        assertThat(count("select count(*) from payment_facts where source_event_id = ?", event.getEventId()))
                .isEqualTo(1);
        assertThat(count("select count(*) from processed_events where event_id = ?", event.getEventId()))
                .isEqualTo(1);
    }

    @Test
    void redeliveredMedicalRecordEventCreatesOneFactRow() {
        MedicalRecordEvent event = medicalRecordEvent(UUID.randomUUID(), UUID.randomUUID());

        analyticsService.processMedicalRecordEvent(event);
        analyticsService.processMedicalRecordEvent(event);

        assertThat(count("select count(*) from medical_record_facts where record_id = ?",
                event.getPayload().getRecordId()))
                .isEqualTo(1);
        assertThat(count("select count(*) from processed_events where event_id = ?", event.getEventId()))
                .isEqualTo(1);
    }

    // ---------- helpers ----------

    private AppointmentEvent appointmentEvent(UUID eventId, UUID actorId) {
        UUID appointmentId = UUID.randomUUID();
        return AppointmentEvent.builder()
                .eventId(eventId)
                .eventVersion(1)
                .eventType("APPOINTMENT_UPDATED")
                .occurredAt(Instant.now())
                .producerService("appointment-service")
                .correlationId(actorId)
                .aggregateId(appointmentId)
                .payload(AppointmentEvent.Payload.builder()
                        .appointmentId(appointmentId)
                        .patientId(UUID.randomUUID())
                        .doctorId(UUID.randomUUID())
                        .startTime(LocalDateTime.now().plusDays(4))
                        .endTime(LocalDateTime.now().plusDays(4).plusMinutes(30))
                        .status("SCHEDULED")
                        .appointmentType("GENERAL")
                        .reason("Checkup")
                        .version(1L)
                        .build())
                .build();
    }

    private BillingEvent paymentConfirmedEvent(UUID eventId, UUID actorId) {
        UUID invoiceId = UUID.randomUUID();
        return BillingEvent.builder()
                .eventId(eventId)
                .eventVersion(1)
                .eventType("PAYMENT_CONFIRMED")
                .occurredAt(Instant.now())
                .producerService("billing-service")
                .correlationId(actorId)
                .aggregateId(invoiceId)
                .payload(BillingEvent.Payload.builder()
                        .invoiceId(invoiceId)
                        .invoiceNumber("INV-IT-0001")
                        .patientId(UUID.randomUUID())
                        .status(InvoiceStatus.PENDING)
                        .currency("USD")
                        .subtotal(new BigDecimal("100.00"))
                        .totalAmount(new BigDecimal("100.00"))
                        .paidAmount(new BigDecimal("40.00"))
                        .paymentAmount(new BigDecimal("40.00"))
                        .paymentMethod(PaymentMethod.CARD)
                        .paymentStatus(PaymentStatus.COMPLETED)
                        .version(1L)
                        .build())
                .build();
    }

    private MedicalRecordEvent medicalRecordEvent(UUID eventId, UUID actorId) {
        return MedicalRecordEvent.builder()
                .eventId(eventId)
                .eventVersion(1)
                .eventType("MEDICAL_RECORD_CREATED")
                .occurredAt(Instant.now())
                .producerService("medical-record-service")
                .correlationId(actorId)
                .aggregateId(UUID.randomUUID())
                .payload(MedicalRecordEvent.Payload.builder()
                        .recordId(UUID.randomUUID())
                        .patientId(UUID.randomUUID())
                        .doctorId(UUID.randomUUID())
                        .encounterDate(LocalDate.now().minusDays(1))
                        .recordType(MedicalRecordType.CONSULTATION)
                        .diagnosis("Common cold")
                        .version(1L)
                        .build())
                .build();
    }

    private int count(String sql, Object argument) {
        return jdbcTemplate.queryForObject(sql, Integer.class, argument);
    }

    private void runConcurrently(int attempts, Runnable task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    try {
                        task.run();
                    } catch (RuntimeException ignored) {
                        // Retryable integrity rejection: the broker redelivers, no duplicate row.
                    }
                    return null;
                }));
            }
            startGate.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
