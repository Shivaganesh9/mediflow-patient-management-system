package com.mediflow.appointment.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.appointment.entity.Appointment;
import com.mediflow.appointment.event.outbox.OutboxEvent;
import com.mediflow.appointment.event.outbox.OutboxEventRepository;
import com.mediflow.appointment.event.outbox.OutboxStatus;
import com.mediflow.common.logging.CorrelationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds appointment domain events and stores them in the transactional outbox.
 *
 * <p>This class deliberately does not talk to Kafka: it is called inside the appointment
 * transaction, so the event row commits or rolls back with the business change. {@code OutboxRelay}
 * performs the broker hand-off.</p>
 */
@Component
@RequiredArgsConstructor
public class AppointmentEventPublisher {

    public static final String TOPIC_APPOINTMENT_CREATED = "appointment.created.v1";
    public static final String TOPIC_APPOINTMENT_UPDATED = "appointment.updated.v1";
    public static final String TOPIC_APPOINTMENT_CANCELLED = "appointment.cancelled.v1";

    private static final String PRODUCER_SERVICE = "appointment-service";
    private static final String EVENT_TYPE_CREATED = "APPOINTMENT_CREATED";
    private static final String EVENT_TYPE_UPDATED = "APPOINTMENT_UPDATED";
    private static final String EVENT_TYPE_CANCELLED = "APPOINTMENT_CANCELLED";

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void publishCreated(Appointment appointment) {
        publish(TOPIC_APPOINTMENT_CREATED, EVENT_TYPE_CREATED, appointment);
    }

    public void publishUpdated(Appointment appointment) {
        publish(TOPIC_APPOINTMENT_UPDATED, EVENT_TYPE_UPDATED, appointment);
    }

    public void publishCancelled(Appointment appointment) {
        publish(TOPIC_APPOINTMENT_CANCELLED, EVENT_TYPE_CANCELLED, appointment);
    }

    private void publish(String topic, String eventType, Appointment appointment) {
        AppointmentEvent.Payload payload = AppointmentEvent.Payload.builder()
                .appointmentId(appointment.getAppointmentId())
                .patientId(appointment.getPatientId())
                .doctorId(appointment.getDoctorId())
                .startTime(appointment.getStartTime())
                .endTime(appointment.getEndTime())
                .status(appointment.getStatus().name())
                .appointmentType(appointment.getAppointmentType())
                .reason(appointment.getReason())
                .version(appointment.getVersion())
                .build();

        AppointmentEvent event = AppointmentEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion(1)
                .eventType(eventType)
                .occurredAt(Instant.now())
                .producerService(PRODUCER_SERVICE)
                .correlationId(CorrelationContext.currentUuid())
                .aggregateId(appointment.getAppointmentId())
                .payload(payload)
                .build();

        enqueue(topic, event);
    }

    private void enqueue(String topic, AppointmentEvent event) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setEventId(event.getEventId());
        outboxEvent.setAggregateId(event.getAggregateId());
        outboxEvent.setTopic(topic);
        outboxEvent.setEventType(event.getEventType());
        outboxEvent.setEventClass(event.getClass().getName());
        outboxEvent.setPayload(serialize(event));
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setAttempts(0);
        outboxEvent.setNextAttemptAt(Instant.now());
        outboxRepository.save(outboxEvent);
    }

    private String serialize(AppointmentEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            // Fails the business transaction rather than committing without a publishable event.
            throw new IllegalStateException("Failed to serialize appointment event", ex);
        }
    }
}
