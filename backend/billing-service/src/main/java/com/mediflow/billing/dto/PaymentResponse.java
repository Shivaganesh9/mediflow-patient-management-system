package com.mediflow.billing.dto;

import com.mediflow.billing.entity.PaymentMethod;
import com.mediflow.billing.entity.PaymentStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class PaymentResponse {
    UUID paymentId;
    UUID invoiceId;
    BigDecimal amount;
    PaymentMethod method;
    String reference;
    PaymentStatus status;
    Instant paidAt;
}
