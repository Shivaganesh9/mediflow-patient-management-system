package com.mediflow.appointment.event;

import com.mediflow.appointment.entity.Appointment;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

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

    private final KafkaTemplate<String, AppointmentEvent> kafkaTemplate;

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
                .aggregateId(appointment.getAppointmentId())
                .payload(payload)
                .build();

        kafkaTemplate.send(topic, appointment.getAppointmentId().toString(), event);
    }
}
