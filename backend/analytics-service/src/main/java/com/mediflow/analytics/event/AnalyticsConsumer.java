package com.mediflow.analytics.event;

import com.mediflow.appointment.event.AppointmentEvent;
import com.mediflow.billing.event.BillingEvent;
import com.mediflow.common.logging.CorrelationContext;
import com.mediflow.medicalrecord.event.MedicalRecordEvent;
import com.mediflow.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsConsumer {

    public static final String TOPIC_APPOINTMENT_CREATED = "appointment.created.v1";
    public static final String TOPIC_APPOINTMENT_UPDATED = "appointment.updated.v1";
    public static final String TOPIC_APPOINTMENT_CANCELLED = "appointment.cancelled.v1";
    public static final String TOPIC_INVOICE_GENERATED = "invoice.generated.v1";
    public static final String TOPIC_INVOICE_CANCELLED = "invoice.cancelled.v1";
    public static final String TOPIC_PAYMENT_CONFIRMED = "payment.confirmed.v1";
    public static final String TOPIC_PAYMENT_REFUNDED = "payment.refunded.v1";
    public static final String TOPIC_MEDICAL_RECORD_CREATED = "medical-record.created.v1";

    private final AnalyticsService analyticsService;

    @KafkaListener(topics = {
            TOPIC_APPOINTMENT_CREATED, TOPIC_APPOINTMENT_UPDATED, TOPIC_APPOINTMENT_CANCELLED
    })
    public void consumeAppointmentEvent(AppointmentEvent event) {
        CorrelationContext.run(correlationIdOf(event), () -> {
            logReceived("appointment", event == null ? null : event.getEventId());
            analyticsService.processAppointmentEvent(event);
        });
    }

    @KafkaListener(topics = {
            TOPIC_INVOICE_GENERATED, TOPIC_INVOICE_CANCELLED,
            TOPIC_PAYMENT_CONFIRMED, TOPIC_PAYMENT_REFUNDED
    })
    public void consumeBillingEvent(BillingEvent event) {
        CorrelationContext.run(correlationIdOf(event), () -> {
            logReceived("billing", event == null ? null : event.getEventId());
            analyticsService.processBillingEvent(event);
        });
    }

    @KafkaListener(topics = {TOPIC_MEDICAL_RECORD_CREATED})
    public void consumeMedicalRecordEvent(MedicalRecordEvent event) {
        CorrelationContext.run(correlationIdOf(event), () -> {
            logReceived("medical record", event == null ? null : event.getEventId());
            analyticsService.processMedicalRecordEvent(event);
        });
    }

    private UUID correlationIdOf(AppointmentEvent event) {
        return event == null ? null : event.getCorrelationId();
    }

    private UUID correlationIdOf(BillingEvent event) {
        return event == null ? null : event.getCorrelationId();
    }

    private UUID correlationIdOf(MedicalRecordEvent event) {
        return event == null ? null : event.getCorrelationId();
    }

    private void logReceived(String kind, UUID eventId) {
        if (eventId == null) {
            log.warn("Received {} event without an event id", kind);
        } else {
            log.debug("Received {} event {}", kind, eventId);
        }
    }
}
