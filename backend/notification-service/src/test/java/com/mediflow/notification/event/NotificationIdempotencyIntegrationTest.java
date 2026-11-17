package com.mediflow.notification.event;

import com.mediflow.appointment.event.AppointmentEvent;
import com.mediflow.notification.AbstractNotificationIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
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
 * Real-PostgreSQL evidence that notification delivery is idempotent.
 *
 * <p>The same domain event can be redelivered by Kafka (at-least-once delivery, consumer restart,
 * or a retry after an unrelated failure). The {@code source_event_id} pre-check plus its unique
 * constraint must keep exactly one notification per event. Runs the real Flyway migrations against
 * PostgreSQL 16 and is skipped automatically when Docker is unavailable.</p>
 */
@SpringBootTest
class NotificationIdempotencyIntegrationTest extends AbstractNotificationIntegrationTest {

    @Autowired
    private NotificationConsumer consumer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void redeliveredAppointmentEventCreatesOneNotification() {
        UUID eventId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        AppointmentEvent event = appointmentCreated(eventId, patientId);

        consumer.consumeAppointmentEvent(event);
        consumer.consumeAppointmentEvent(event);
        consumer.consumeAppointmentEvent(event);

        assertThat(notificationCount(eventId))
                .as("Kafka redelivery must not create duplicate notifications")
                .isEqualTo(1);
        assertThat(notificationCountForRecipient(patientId)).isEqualTo(1);
    }

    @Test
    void concurrentRedeliveryOfTheSameEventCreatesOneNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        AppointmentEvent event = appointmentCreated(eventId, patientId);

        // The loser of the race may fail with a retryable integrity error (the broker would
        // redeliver it, and the pre-check then skips it). What must never happen is two rows.
        runConcurrently(6, () -> consumer.consumeAppointmentEvent(event));

        assertThat(notificationCount(eventId))
                .as("concurrent duplicate delivery must converge on a single notification")
                .isEqualTo(1);
        assertThat(notificationCountForRecipient(patientId)).isEqualTo(1);
    }

    @Test
    void distinctEventsForTheSamePatientCreateDistinctNotifications() {
        UUID patientId = UUID.randomUUID();
        UUID firstEvent = UUID.randomUUID();
        UUID secondEvent = UUID.randomUUID();

        consumer.consumeAppointmentEvent(appointmentCreated(firstEvent, patientId));
        consumer.consumeAppointmentEvent(appointmentCreated(secondEvent, patientId));

        assertThat(notificationCount(firstEvent)).isEqualTo(1);
        assertThat(notificationCount(secondEvent)).isEqualTo(1);
        assertThat(notificationCountForRecipient(patientId)).isEqualTo(2);
    }

    // ---------- helpers ----------

    private AppointmentEvent appointmentCreated(UUID eventId, UUID patientId) {
        return AppointmentEvent.builder()
                .eventId(eventId)
                .eventVersion(1)
                .eventType("APPOINTMENT_CREATED")
                .occurredAt(Instant.now())
                .producerService("appointment-service")
                .correlationId(UUID.randomUUID())
                .aggregateId(UUID.randomUUID())
                .payload(AppointmentEvent.Payload.builder()
                        .appointmentId(UUID.randomUUID())
                        .patientId(patientId)
                        .doctorId(UUID.randomUUID())
                        .startTime(LocalDateTime.now().plusDays(3))
                        .endTime(LocalDateTime.now().plusDays(3).plusMinutes(30))
                        .status("SCHEDULED")
                        .reason("Checkup")
                        .build())
                .build();
    }

    private int notificationCount(UUID sourceEventId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from notifications where source_event_id = ?",
                Integer.class, sourceEventId);
    }

    private int notificationCountForRecipient(UUID recipientId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from notifications where recipient_id = ?",
                Integer.class, recipientId);
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
                        // A retryable integrity rejection is acceptable; duplicates are not.
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
