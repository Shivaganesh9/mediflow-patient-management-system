package com.mediflow.notification.mapper;

import com.mediflow.appointment.event.AppointmentEvent;
import com.mediflow.billing.event.BillingEvent;
import com.mediflow.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationEventMapperTest {

    private final NotificationEventMapper mapper = new NotificationEventMapper();

    @Test
    void shouldMapAppointmentCreatedEvent() {
        UUID patientId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        AppointmentEvent event = appointmentEvent(eventId, patientId, "APPOINTMENT_CREATED",
                LocalDateTime.of(2026, 8, 13, 10, 0));

        Optional<NotificationDraft> draft = mapper.fromAppointmentEvent(event);

        assertTrue(draft.isPresent());
        assertEquals(eventId, draft.get().sourceEventId());
        assertEquals(patientId, draft.get().recipientId());
        assertEquals(NotificationType.APPOINTMENT_CREATED, draft.get().notificationType());
        assertEquals("IN_APP", draft.get().channel());
        assertTrue(draft.get().message().contains("2026-08-13T10:00"));
    }

    @Test
    void shouldMapAppointmentUpdatedEvent() {
        AppointmentEvent event = appointmentEvent(UUID.randomUUID(), UUID.randomUUID(),
                "APPOINTMENT_UPDATED", LocalDateTime.of(2026, 8, 14, 15, 30));

        Optional<NotificationDraft> draft = mapper.fromAppointmentEvent(event);

        assertTrue(draft.isPresent());
        assertEquals(NotificationType.APPOINTMENT_UPDATED, draft.get().notificationType());
        assertTrue(draft.get().message().contains("rescheduled"));
    }

    @Test
    void shouldMapAppointmentCancelledEvent() {
        AppointmentEvent event = appointmentEvent(UUID.randomUUID(), UUID.randomUUID(),
                "APPOINTMENT_CANCELLED", LocalDateTime.of(2026, 8, 14, 15, 30));

        Optional<NotificationDraft> draft = mapper.fromAppointmentEvent(event);

        assertTrue(draft.isPresent());
        assertEquals(NotificationType.APPOINTMENT_CANCELLED, draft.get().notificationType());
        assertTrue(draft.get().message().contains("cancelled"));
    }

    @Test
    void shouldReturnEmptyForUnknownAppointmentEventType() {
        AppointmentEvent event = appointmentEvent(UUID.randomUUID(), UUID.randomUUID(),
                "UNKNOWN_EVENT", LocalDateTime.now());

        assertTrue(mapper.fromAppointmentEvent(event).isEmpty());
    }

    @Test
    void shouldReturnEmptyForNullAppointmentEvent() {
        assertTrue(mapper.fromAppointmentEvent(null).isEmpty());
    }

    @Test
    void shouldReturnEmptyForAppointmentEventWithoutPatient() {
        AppointmentEvent event = new AppointmentEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("APPOINTMENT_CREATED");
        event.setPayload(new AppointmentEvent.Payload());

        assertTrue(mapper.fromAppointmentEvent(event).isEmpty());
    }

    @Test
    void shouldMapInvoiceGeneratedEvent() {
        UUID patientId = UUID.randomUUID();
        BillingEvent event = billingEvent(UUID.randomUUID(), patientId, "INVOICE_GENERATED");

        Optional<NotificationDraft> draft = mapper.fromBillingEvent(event);

        assertTrue(draft.isPresent());
        assertEquals(patientId, draft.get().recipientId());
        assertEquals(NotificationType.INVOICE_GENERATED, draft.get().notificationType());
        assertTrue(draft.get().message().contains("INV-2026-001"));
    }

    @Test
    void shouldMapPaymentConfirmedEvent() {
        BillingEvent event = billingEvent(UUID.randomUUID(), UUID.randomUUID(), "PAYMENT_CONFIRMED");

        Optional<NotificationDraft> draft = mapper.fromBillingEvent(event);

        assertTrue(draft.isPresent());
        assertEquals(NotificationType.PAYMENT_CONFIRMED, draft.get().notificationType());
        assertTrue(draft.get().message().contains("150.00"));
    }

    @Test
    void shouldReturnEmptyForUnknownBillingEventType() {
        BillingEvent event = billingEvent(UUID.randomUUID(), UUID.randomUUID(), "UNKNOWN_EVENT");

        assertTrue(mapper.fromBillingEvent(event).isEmpty());
    }

    @Test
    void shouldReturnEmptyForNullBillingEvent() {
        assertTrue(mapper.fromBillingEvent(null).isEmpty());
    }

    @Test
    void shouldReturnEmptyForBillingEventWithoutPatient() {
        BillingEvent event = new BillingEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("INVOICE_GENERATED");
        event.setPayload(new BillingEvent.Payload());

        assertTrue(mapper.fromBillingEvent(event).isEmpty());
    }

    private AppointmentEvent appointmentEvent(UUID eventId, UUID patientId, String eventType, LocalDateTime startTime) {
        return AppointmentEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .payload(AppointmentEvent.Payload.builder()
                        .patientId(patientId)
                        .startTime(startTime)
                        .build())
                .build();
    }

    private BillingEvent billingEvent(UUID eventId, UUID patientId, String eventType) {
        return BillingEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .payload(BillingEvent.Payload.builder()
                        .patientId(patientId)
                        .invoiceNumber("INV-2026-001")
                        .currency("USD")
                        .totalAmount(new java.math.BigDecimal("150.00"))
                        .paymentAmount(new java.math.BigDecimal("150.00"))
                        .build())
                .build();
    }
}
