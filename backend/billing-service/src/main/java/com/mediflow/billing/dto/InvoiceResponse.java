package com.mediflow.billing.dto;

import com.mediflow.billing.entity.InvoiceStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class InvoiceResponse {
    UUID invoiceId;
    String invoiceNumber;
    UUID patientId;
    UUID appointmentId;
    InvoiceStatus status;
    String currency;
    BigDecimal subtotal;
    BigDecimal taxRate;
    BigDecimal taxAmount;
    BigDecimal discount;
    BigDecimal totalAmount;
    BigDecimal paidAmount;
    BigDecimal remainingAmount;
    LocalDate dueDate;
    Instant issuedAt;
    Instant paidAt;
    Instant cancelledAt;
    String notes;
    Long version;
    List<InvoiceItemResponse> items;
    List<PaymentResponse> payments;
}
