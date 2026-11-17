package com.mediflow.billing.service;

import com.mediflow.billing.entity.Invoice;
import com.mediflow.billing.entity.Payment;

/**
 * Result of applying a payment: the recomputed invoice plus the payment row that was created, so
 * the idempotency claim can reference the exact result it protects.
 */
public record PaymentReceipt(Invoice invoice, Payment payment) {
}
