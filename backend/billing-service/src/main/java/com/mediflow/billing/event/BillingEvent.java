package com.mediflow.billing.event;

import com.mediflow.billing.entity.InvoiceStatus;
import com.mediflow.billing.entity.PaymentMethod;
import com.mediflow.billing.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingEvent {

    private UUID eventId;
    private int eventVersion;
    private String eventType;
    private Instant occurredAt;
    private String producerService;
    private UUID correlationId;
    private UUID causationId;
    private UUID aggregateId;
    private Payload payload;
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payload {
        private UUID invoiceId;
        private String invoiceNumber;
        private UUID patientId;
        private UUID appointmentId;
        private InvoiceStatus status;
        private String currency;
        private BigDecimal subtotal;
        private BigDecimal taxAmount;
        private BigDecimal discount;
        private BigDecimal totalAmount;
        private BigDecimal paidAmount;
        private BigDecimal paymentAmount;
        private PaymentMethod paymentMethod;
        private PaymentStatus paymentStatus;
        private Long version;
    }
}
