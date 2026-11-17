package com.mediflow.billing.entity;

import com.mediflow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Claim on an {@code Idempotency-Key} supplied when recording a payment.
 *
 * <p>The row is inserted in the same transaction as the payment it protects, so a rolled-back
 * payment never leaves a claim behind. The unique constraint on {@code (requester, idempotency_key)}
 * is what serialises concurrent retries: the loser reads this row and replays the stored result.
 * {@code requestFingerprint} distinguishes a genuine retry (same request) from key reuse with a
 * different payload, which must fail with HTTP 409.</p>
 */
@Entity
@Table(name = "payment_idempotency", uniqueConstraints = @UniqueConstraint(
        name = "uq_payment_idempotency_scope_key", columnNames = {"requester", "idempotency_key"}))
@Getter
@Setter
@NoArgsConstructor
public class PaymentIdempotencyRecord extends BaseEntity {

    @Column(name = "idempotency_key", nullable = false, length = 80)
    private String idempotencyKey;

    /** Caller scope (authenticated username), so unrelated callers can reuse the same key. */
    @Column(name = "requester", nullable = false, length = 255)
    private String requester;

    /** Public invoice identifier ({@code invoices.invoice_id}), i.e. the id used in the API path. */
    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    /** SHA-256 over the canonical payment request; never contains card or patient data. */
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    /** Payment created by the original request; absent only while that transaction is in flight. */
    @Column(name = "payment_id")
    private UUID paymentId;
}
