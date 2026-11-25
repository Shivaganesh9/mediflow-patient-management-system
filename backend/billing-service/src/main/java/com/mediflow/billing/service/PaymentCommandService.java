package com.mediflow.billing.service;

import com.mediflow.billing.dto.RecordPaymentRequest;
import com.mediflow.billing.entity.Invoice;
import com.mediflow.billing.entity.PaymentIdempotencyRecord;
import com.mediflow.billing.repository.InvoiceRepository;
import com.mediflow.billing.repository.PaymentIdempotencyRepository;
import com.mediflow.billing.security.AccessControlService;
import com.mediflow.common.exception.ConflictException;
import com.mediflow.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Records payments with optional idempotency-key protection.
 *
 * <p>Semantics of the {@code Idempotency-Key} header on
 * {@code POST /api/v1/invoices/{invoiceId}/payments}:</p>
 * <ul>
 *   <li><strong>Same key, same request</strong> &rarr; the original result is returned again and no
 *       second payment is created ({@code replayed = true}).</li>
 *   <li><strong>Same key, different request</strong> &rarr; HTTP 409: the key is already bound to a
 *       different payload.</li>
 *   <li><strong>Concurrent requests with the same key</strong> &rarr; the unique constraint on
 *       {@code (requester, idempotency_key)} serialises them: one wins, the others replay.</li>
 *   <li><strong>No key</strong> &rarr; the legacy behaviour (a new payment per request). Clients
 *       that retry must send a key.</li>
 * </ul>
 *
 * <p>The claim row, the payment and the outbox event share one transaction, so a failure never
 * leaves an idempotency record without its payment (or vice versa). This is an internal
 * duplicate-suppression mechanism: it does <em>not</em> provide the guarantees of an external
 * payment provider (settlement, charge disputes, provider-side idempotency).</p>
 */
@Service
@Slf4j
public class PaymentCommandService {

    /** Minimum/maximum length and characters accepted for a caller-supplied idempotency key. */
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,80}");

    private static final String DIFFERENT_REQUEST_MESSAGE =
            "Idempotency-Key has already been used for a different payment request";

    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final PaymentIdempotencyRepository idempotencyRepository;
    private final AccessControlService accessControl;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;

    public PaymentCommandService(InvoiceService invoiceService,
                                 InvoiceRepository invoiceRepository,
                                 PaymentIdempotencyRepository idempotencyRepository,
                                 AccessControlService accessControl,
                                 PlatformTransactionManager transactionManager) {
        this.invoiceService = invoiceService;
        this.invoiceRepository = invoiceRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.accessControl = accessControl;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    /**
     * @param idempotencyKey raw {@code Idempotency-Key} header, or {@code null} for a non-idempotent
     *                       legacy call
     */
    @PreAuthorize("hasAnyRole('ADMIN','BILLING_STAFF')")
    public PaymentResult recordPayment(UUID invoiceId, RecordPaymentRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new PaymentResult(invoiceService.recordPayment(invoiceId, request), false);
        }
        String key = requireValidKey(idempotencyKey);
        String requester = requesterScope();
        String fingerprint = fingerprint(invoiceId, request);

        try {
            // One transaction for the claim, the payment and the outbox row: either all of them
            // become visible together or none of them do.
            Invoice invoice = writeTransaction.execute(status -> {
                PaymentIdempotencyRecord claim = new PaymentIdempotencyRecord();
                claim.setIdempotencyKey(key);
                claim.setRequester(requester);
                claim.setInvoiceId(invoiceId);
                claim.setRequestFingerprint(fingerprint);
                claim.setCreatedBy(requester);
                idempotencyRepository.saveAndFlush(claim);

                PaymentReceipt receipt = invoiceService.applyPayment(invoiceId, request);
                claim.setPaymentId(receipt.payment().getPaymentId());
                claim.setUpdatedBy(requester);
                return receipt.invoice();
            });
            return new PaymentResult(invoice, false);
        } catch (DataIntegrityViolationException ex) {
            // The claim insert lost a race (or the key was already used). Re-read it in its own
            // transaction, because the failed transaction is rolled back and unusable.
            return replay(invoiceId, requester, key, fingerprint, ex);
        }
    }

    private PaymentResult replay(UUID invoiceId, String requester, String key, String fingerprint,
                                 DataIntegrityViolationException original) {
        return readTransaction.execute(status -> {
            Optional<PaymentIdempotencyRecord> existing =
                    idempotencyRepository.findByRequesterAndIdempotencyKey(requester, key);
            if (existing.isEmpty()) {
                // Not an idempotency collision: surface the original failure so it is reported
                // accurately instead of being swallowed as a replay.
                throw original;
            }
            if (!existing.get().getRequestFingerprint().equals(fingerprint)) {
                log.warn("Idempotency-Key reuse with a different payload for invoice {}", invoiceId);
                throw new ConflictException(DIFFERENT_REQUEST_MESSAGE);
            }
            Invoice invoice = invoiceRepository.findByInvoiceId(invoiceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));
            // open-in-view is disabled, so initialise the response collections in this transaction.
            invoice.getItems().size();
            invoice.getPayments().size();
            log.info("Replaying recorded payment {} for idempotency key {}",
                    existing.get().getPaymentId(), key);
            return new PaymentResult(invoice, true);
        });
    }

    private String requireValidKey(String idempotencyKey) {
        String key = idempotencyKey.trim();
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must be 8-80 characters of letters, digits, '.', '_', ':' or '-'");
        }
        return key;
    }

    private String requesterScope() {
        String username = accessControl.currentUsername();
        if (username != null && !username.isBlank()) {
            return username;
        }
        UUID userId = accessControl.currentUserId();
        return userId != null ? userId.toString() : "unknown";
    }

    /**
     * Canonical request identity. Amounts are normalised to two decimals so {@code 25} and
     * {@code 25.00} are the same request, and a blank reference is treated like an absent one.
     */
    private String fingerprint(UUID invoiceId, RecordPaymentRequest request) {
        BigDecimal amount = request.getAmount() == null
                ? BigDecimal.ZERO
                : request.getAmount().setScale(2, RoundingMode.HALF_UP);
        String reference = request.getReference() == null ? "" : request.getReference().trim();
        String canonical = invoiceId + "|" + amount + "|" + request.getMethod() + "|" + reference;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    /** Outcome of a payment command; {@code replayed} is true when no new payment was created. */
    public record PaymentResult(Invoice invoice, boolean replayed) {
    }
}
