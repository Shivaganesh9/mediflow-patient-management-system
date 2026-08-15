package com.mediflow.analytics.mapper;

import com.mediflow.analytics.entity.AppointmentFact;
import com.mediflow.analytics.entity.InvoiceFact;
import com.mediflow.analytics.entity.MedicalRecordFact;
import com.mediflow.analytics.entity.PaymentFact;
import com.mediflow.appointment.event.AppointmentEvent;
import com.mediflow.billing.entity.InvoiceStatus;
import com.mediflow.billing.entity.PaymentMethod;
import com.mediflow.billing.entity.PaymentStatus;
import com.mediflow.billing.event.BillingEvent;
import com.mediflow.medicalrecord.entity.MedicalRecordType;
import com.mediflow.medicalrecord.event.MedicalRecordEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsEventMapperTest {

    private final AnalyticsEventMapper mapper = new AnalyticsEventMapper();

    @Test
    void shouldMapAppointmentEvent() {
        UUID eventId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        AppointmentEvent event = appointmentEvent(eventId, appointmentId, patientId, doctorId,
                LocalDateTime.of(2026, 8, 14, 10, 0), "COMPLETED", "CONSULTATION");

        Optional<AppointmentFact> fact = mapper.toAppointmentFact(event);

        assertTrue(fact.isPresent());
        assertEquals(appointmentId, fact.get().getAppointmentId());
        assertEquals(eventId, fact.get().getSourceEventId());
        assertEquals(patientId, fact.get().getPatientId());
        assertEquals(doctorId, fact.get().getDoctorId());
        assertEquals(LocalDate.of(2026, 8, 14), fact.get().getAppointmentDate());
        assertEquals("COMPLETED", fact.get().getStatus());
        assertEquals("CONSULTATION", fact.get().getAppointmentType());
    }

    @Test
    void shouldReturnEmptyForNullAppointmentEvent() {
        assertTrue(mapper.toAppointmentFact(null).isEmpty());
    }

    @Test
    void shouldReturnEmptyForAppointmentEventWithoutAppointmentId() {
        AppointmentEvent event = new AppointmentEvent();
        event.setEventId(UUID.randomUUID());
        event.setPayload(new AppointmentEvent.Payload());

        assertTrue(mapper.toAppointmentFact(event).isEmpty());
    }

    @Test
    void shouldMapInvoiceGeneratedEvent() {
        UUID eventId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        BillingEvent event = invoiceEvent(eventId, invoiceId, patientId, InvoiceStatus.PENDING, "100.00");

        Optional<InvoiceFact> fact = mapper.toInvoiceFact(event);

        assertTrue(fact.isPresent());
        assertEquals(invoiceId, fact.get().getInvoiceId());
        assertEquals(eventId, fact.get().getSourceEventId());
        assertEquals(patientId, fact.get().getPatientId());
        assertEquals("PENDING", fact.get().getStatus());
        assertEquals(new BigDecimal("100.00"), fact.get().getTotalAmount());
        assertEquals(event.getOccurredAt(), fact.get().getInvoiceDate());
    }

    @Test
    void shouldMapInvoiceCancelledEvent() {
        BillingEvent event = invoiceEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                InvoiceStatus.CANCELLED, "250.00");

        Optional<InvoiceFact> fact = mapper.toInvoiceFact(event);

        assertTrue(fact.isPresent());
        assertEquals("CANCELLED", fact.get().getStatus());
    }

    @Test
    void shouldMapPaymentConfirmedEvent() {
        UUID eventId = UUID.randomUUID();
        BillingEvent event = paymentEvent(eventId, InvoiceStatus.PAID, PaymentStatus.COMPLETED,
                PaymentMethod.CARD, "150.00");

        Optional<PaymentFact> fact = mapper.toPaymentFact(event);

        assertTrue(fact.isPresent());
        assertEquals(eventId, fact.get().getSourceEventId());
        assertEquals(new BigDecimal("150.00"), fact.get().getAmount());
        assertEquals("COMPLETED", fact.get().getStatus());
        assertEquals("CARD", fact.get().getMethod());
    }

    @Test
    void shouldMapPaymentRefundedEvent() {
        BillingEvent event = paymentEvent(UUID.randomUUID(), InvoiceStatus.PAID, PaymentStatus.REFUNDED,
                PaymentMethod.CARD, "75.00");

        Optional<PaymentFact> fact = mapper.toPaymentFact(event);

        assertTrue(fact.isPresent());
        assertEquals("REFUNDED", fact.get().getStatus());
        assertEquals(new BigDecimal("75.00"), fact.get().getAmount());
    }

    @Test
    void shouldReturnEmptyForNullBillingEvent() {
        assertTrue(mapper.toInvoiceFact(null).isEmpty());
        assertTrue(mapper.toPaymentFact(null).isEmpty());
    }

    @Test
    void shouldMapMedicalRecordEvent() {
        UUID eventId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        MedicalRecordEvent event = medicalRecordEvent(eventId, recordId, patientId, doctorId,
                LocalDate.of(2026, 8, 12), MedicalRecordType.CONSULTATION);

        Optional<MedicalRecordFact> fact = mapper.toMedicalRecordFact(event);

        assertTrue(fact.isPresent());
        assertEquals(recordId, fact.get().getRecordId());
        assertEquals(eventId, fact.get().getSourceEventId());
        assertEquals(patientId, fact.get().getPatientId());
        assertEquals(doctorId, fact.get().getDoctorId());
        assertEquals(LocalDate.of(2026, 8, 12), fact.get().getEncounterDate());
        assertEquals("CONSULTATION", fact.get().getRecordType());
    }

    @Test
    void shouldReturnEmptyForNullMedicalRecordEvent() {
        assertTrue(mapper.toMedicalRecordFact(null).isEmpty());
    }

    @Test
    void shouldReturnEmptyForMedicalRecordEventWithoutRecordId() {
        MedicalRecordEvent event = new MedicalRecordEvent();
        event.setEventId(UUID.randomUUID());
        event.setPayload(new MedicalRecordEvent.Payload());

        assertTrue(mapper.toMedicalRecordFact(event).isEmpty());
    }

    private AppointmentEvent appointmentEvent(UUID eventId, UUID appointmentId, UUID patientId, UUID doctorId,
                                              LocalDateTime startTime, String status, String appointmentType) {
        return AppointmentEvent.builder()
                .eventId(eventId)
                .eventType("APPOINTMENT_COMPLETED")
                .occurredAt(Instant.now())
                .payload(AppointmentEvent.Payload.builder()
                        .appointmentId(appointmentId)
                        .patientId(patientId)
                        .doctorId(doctorId)
                        .startTime(startTime)
                        .status(status)
                        .appointmentType(appointmentType)
                        .build())
                .build();
    }

    private BillingEvent invoiceEvent(UUID eventId, UUID invoiceId, UUID patientId,
                                      InvoiceStatus status, String totalAmount) {
        return BillingEvent.builder()
                .eventId(eventId)
                .eventType("INVOICE_GENERATED")
                .occurredAt(Instant.now())
                .payload(BillingEvent.Payload.builder()
                        .invoiceId(invoiceId)
                        .invoiceNumber("INV-2026-001")
                        .patientId(patientId)
                        .status(status)
                        .currency("USD")
                        .totalAmount(new BigDecimal(totalAmount))
                        .build())
                .build();
    }

    private BillingEvent paymentEvent(UUID eventId, InvoiceStatus invoiceStatus, PaymentStatus paymentStatus,
                                      PaymentMethod method, String amount) {
        return BillingEvent.builder()
                .eventId(eventId)
                .eventType("PAYMENT_CONFIRMED")
                .occurredAt(Instant.now())
                .payload(BillingEvent.Payload.builder()
                        .invoiceId(UUID.randomUUID())
                        .patientId(UUID.randomUUID())
                        .status(invoiceStatus)
                        .currency("USD")
                        .paymentAmount(new BigDecimal(amount))
                        .paymentMethod(method)
                        .paymentStatus(paymentStatus)
                        .build())
                .build();
    }

    private MedicalRecordEvent medicalRecordEvent(UUID eventId, UUID recordId, UUID patientId, UUID doctorId,
                                                  LocalDate encounterDate, MedicalRecordType recordType) {
        return MedicalRecordEvent.builder()
                .eventId(eventId)
                .eventType("MEDICAL_RECORD_CREATED")
                .occurredAt(Instant.now())
                .payload(MedicalRecordEvent.Payload.builder()
                        .recordId(recordId)
                        .patientId(patientId)
                        .doctorId(doctorId)
                        .encounterDate(encounterDate)
                        .recordType(recordType)
                        .build())
                .build();
    }
}