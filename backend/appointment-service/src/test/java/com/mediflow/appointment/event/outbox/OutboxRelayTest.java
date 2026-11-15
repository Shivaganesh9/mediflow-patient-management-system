package com.mediflow.appointment.event.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.appointment.event.AppointmentEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(repository, kafkaTemplate, objectMapper,
                new SimpleMeterRegistry(), 20, MAX_ATTEMPTS, 1000);
    }

    @Test
    void publishesPendingEventAndMarksItPublished() {
        OutboxEvent event = pendingEvent(0);
        when(repository.lockPendingBatch(any(), any())).thenReturn(List.of(event));
        when(kafkaTemplate.send(eq("appointment.created.v1"), eq(event.getAggregateId().toString()), any()))
                .thenReturn(completedSend());

        relay.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isZero();
        verify(repository).save(event);
    }

    @Test
    void failureSchedulesRetryAndStopsBatchToPreserveOrdering() {
        OutboxEvent first = pendingEvent(0);
        OutboxEvent second = pendingEvent(0);
        when(repository.lockPendingBatch(any(), any())).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedSend());

        relay.relay();

        assertThat(first.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(second.getAttempts()).isZero();
        assertThat(second.getStatus()).isEqualTo(OutboxStatus.PENDING);
        // Only the first event attempted; the second is left for the next tick.
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
        verify(repository, times(1)).save(any());
    }

    @Test
    void exhaustedRetriesMoveEventToDeadLetterState() {
        OutboxEvent event = pendingEvent(MAX_ATTEMPTS - 1);
        when(repository.lockPendingBatch(any(), any())).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedSend());

        relay.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(event.getAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(event.getLastError()).contains("broker unavailable");
        verify(repository).save(event);
    }

    @Test
    void corruptPayloadIsNotSentAndIsRetried() {
        OutboxEvent event = pendingEvent(0);
        event.setPayload("{not-json");
        when(repository.lockPendingBatch(any(), any())).thenReturn(List.of(event));

        relay.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        verify(repository).save(event);
    }

    private OutboxEvent pendingEvent(int attempts) {
        AppointmentEvent event = AppointmentEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion(1)
                .eventType("APPOINTMENT_CREATED")
                .occurredAt(Instant.now())
                .producerService("appointment-service")
                .aggregateId(UUID.randomUUID())
                .payload(AppointmentEvent.Payload.builder()
                        .appointmentId(UUID.randomUUID())
                        .patientId(UUID.randomUUID())
                        .doctorId(UUID.randomUUID())
                        .startTime(LocalDateTime.now().plusDays(1))
                        .endTime(LocalDateTime.now().plusDays(1).plusHours(1))
                        .status("SCHEDULED")
                        .reason("Checkup")
                        .build())
                .build();

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setEventId(event.getEventId());
        outboxEvent.setAggregateId(event.getAggregateId());
        outboxEvent.setTopic("appointment.created.v1");
        outboxEvent.setEventType(event.getEventType());
        outboxEvent.setEventClass(AppointmentEvent.class.getName());
        try {
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setAttempts(attempts);
        outboxEvent.setNextAttemptAt(Instant.now().minusSeconds(1));
        return outboxEvent;
    }

    private CompletableFuture<SendResult<String, Object>> completedSend() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(null);
        return future;
    }

    private CompletableFuture<SendResult<String, Object>> failedSend() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException("broker unavailable"));
        return future;
    }
}
