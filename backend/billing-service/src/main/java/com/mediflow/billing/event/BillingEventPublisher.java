package com.mediflow.billing.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.entity.Invoice;
import com.mediflow.billing.entity.Payment;
import com.mediflow.billing.event.outbox.OutboxEvent;
import com.mediflow.billing.event.outbox.OutboxEventRepository;
import com.mediflow.billing.event.outbox.OutboxStatus;
import com.mediflow.common.logging.CorrelationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds billing domain events and stores them in the transactional outbox.
 *
 * <p>Publishing to Kafka is intentionally deferred to {@code OutboxRelay} so the invoice/payment
 * database commit and the event are atomic. A payment confirmation can therefore never be lost
 * because the broker was briefly unavailable after the database committed.</p>
 */
@Component
@RequiredArgsConstructor
public class BillingEventPublisher {

    public static final String TOPIC_INVOICE_GENERATED = "invoice.generated.v1";
    public static final String TOPIC_INVOICE_CANCELLED = "invoice.cancelled.v1";
    public static final String TOPIC_PAYMENT_CONFIRMED = "payment.confirmed.v1";
    public static final String TOPIC_PAYMENT_REFUNDED = "payment.refunded.v1";

    private static final String PRODUCER_SERVICE = "billing-service";
    private static final String EVENT_TYPE_INVOICE_GENERATED = "INVOICE_GENERATED";
    private static final String EVENT_TYPE_INVOICE_CANCELLED = "INVOICE_CANCELLED";
    private static final String EVENT_TYPE_PAYMENT_CONFIRMED = "PAYMENT_CONFIRMED";
    private static final String EVENT_TYPE_PAYMENT_REFUNDED = "PAYMENT_REFUNDED";

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void publishInvoiceGenerated(Invoice invoice) {
        publish(TOPIC_INVOICE_GENERATED, EVENT_TYPE_INVOICE_GENERATED, invoice, null);
    }

    public void publishInvoiceCancelled(Invoice invoice) {
        publish(TOPIC_INVOICE_CANCELLED, EVENT_TYPE_INVOICE_CANCELLED, invoice, null);
    }

    public void publishPaymentConfirmed(Invoice invoice, Payment payment) {
        publish(TOPIC_PAYMENT_CONFIRMED, EVENT_TYPE_PAYMENT_CONFIRMED, invoice, payment);
    }

    public void publishPaymentRefunded(Invoice invoice, Payment payment) {
        publish(TOPIC_PAYMENT_REFUNDED, EVENT_TYPE_PAYMENT_REFUNDED, invoice, payment);
    }

    private void publish(String topic, String eventType, Invoice invoice, Payment payment) {
        BillingEvent.Payload payload = BillingEvent.Payload.builder()
                .invoiceId(invoice.getInvoiceId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .patientId(invoice.getPatientId())
                .appointmentId(invoice.getAppointmentId())
                .status(invoice.getStatus())
                .currency(invoice.getCurrency())
                .subtotal(invoice.getSubtotal())
                .taxAmount(invoice.getTaxAmount())
                .discount(invoice.getDiscount())
                .totalAmount(invoice.getTotalAmount())
                .paidAmount(invoice.getPaidAmount())
                .version(invoice.getVersion())
                .build();

        if (payment != null) {
            payload.setPaymentAmount(payment.getAmount());
            payload.setPaymentMethod(payment.getMethod());
            payload.setPaymentStatus(payment.getStatus());
        }

        BillingEvent event = BillingEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion(1)
                .eventType(eventType)
                .occurredAt(Instant.now())
                .producerService(PRODUCER_SERVICE)
                .correlationId(CorrelationContext.currentUuid())
                .aggregateId(invoice.getInvoiceId())
                .payload(payload)
                .build();

        enqueue(topic, event);
    }

    private void enqueue(String topic, BillingEvent event) {
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

    private String serialize(BillingEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize billing event", ex);
        }
    }
}
