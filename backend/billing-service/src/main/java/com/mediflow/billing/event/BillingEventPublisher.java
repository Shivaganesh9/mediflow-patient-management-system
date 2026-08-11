package com.mediflow.billing.event;

import com.mediflow.billing.entity.Invoice;
import com.mediflow.billing.entity.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

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

    private final KafkaTemplate<String, BillingEvent> kafkaTemplate;

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
                .aggregateId(invoice.getInvoiceId())
                .payload(payload)
                .build();

        kafkaTemplate.send(topic, invoice.getInvoiceId().toString(), event);
    }
}
