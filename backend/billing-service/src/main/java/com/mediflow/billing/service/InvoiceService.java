package com.mediflow.billing.service;

import com.mediflow.billing.dto.CreateInvoiceItemRequest;
import com.mediflow.billing.dto.CreateInvoiceRequest;
import com.mediflow.billing.dto.RecordPaymentRequest;
import com.mediflow.billing.dto.UpdateInvoiceRequest;
import com.mediflow.billing.entity.Invoice;
import com.mediflow.billing.entity.InvoiceItem;
import com.mediflow.billing.entity.InvoiceStatus;
import com.mediflow.billing.entity.Payment;
import com.mediflow.billing.entity.PaymentStatus;
import com.mediflow.billing.event.BillingEventPublisher;
import com.mediflow.billing.repository.InvoiceRepository;
import com.mediflow.billing.repository.PaymentRepository;
import com.mediflow.billing.security.AccessControlService;
import com.mediflow.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final BillingEventPublisher eventPublisher;
    private final AccessControlService accessControl;

    @Transactional
    public Invoice createInvoice(CreateInvoiceRequest request) {
        List<InvoiceItem> items = buildItems(request.getItems(), null);

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setPatientId(request.getPatientId());
        invoice.setAppointmentId(request.getAppointmentId());
        invoice.setCurrency(request.getCurrency().toUpperCase());
        invoice.setDueDate(request.getDueDate());
        invoice.setNotes(request.getNotes());
        invoice.setTaxRate(money(request.getTaxRate()));
        invoice.setDiscount(money(request.getDiscount()));
        invoice.setIssuedAt(Instant.now());
        invoice.setStatus(InvoiceStatus.PENDING);

        applyItems(invoice, items);
        applyTotals(invoice);
        requirePositiveTotal(invoice);

        invoice = invoiceRepository.save(invoice);
        eventPublisher.publishInvoiceGenerated(invoice);
        return invoice;
    }

    @Transactional(readOnly = true)
    public Page<Invoice> searchInvoices(InvoiceStatus status, UUID patientId, UUID appointmentId, Pageable pageable) {
        UUID scopedPatientId = accessControl.scopedPatientId(patientId);
        Page<Invoice> invoices = invoiceRepository.search(status, scopedPatientId, appointmentId, pageable);
        // open-in-view is disabled: initialize the response collections inside the transaction,
        // batched by @BatchSize to avoid a per-invoice N+1 query.
        invoices.forEach(invoice -> {
            invoice.getItems().size();
            invoice.getPayments().size();
        });
        return invoices;
    }

    @Transactional(readOnly = true)
    public Invoice getInvoice(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));
        accessControl.assertCanRead(invoice);
        invoice.getItems().size();
        invoice.getPayments().size();
        return invoice;
    }

    @Transactional(readOnly = true)
    public List<Payment> getPayments(UUID invoiceId) {
        Invoice invoice = getInvoice(invoiceId);
        return invoice.getPayments();
    }

    @Transactional
    public Invoice updateInvoice(UUID invoiceId, UpdateInvoiceRequest request) {
        Invoice invoice = invoiceRepository.findByInvoiceIdForUpdate(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));

        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new IllegalArgumentException("Invoice cannot be modified in status " + invoice.getStatus());
        }

        if (request.getItems() != null) {
            invoice.getItems().clear();
            invoice.getItems().addAll(buildItems(request.getItems(), invoice));
        }
        if (request.getTaxRate() != null) {
            invoice.setTaxRate(money(request.getTaxRate()));
        }
        if (request.getDiscount() != null) {
            invoice.setDiscount(money(request.getDiscount()));
        }
        if (request.getDueDate() != null) {
            invoice.setDueDate(request.getDueDate());
        }
        if (request.getNotes() != null) {
            invoice.setNotes(request.getNotes());
        }

        applyTotals(invoice);
        requirePositiveTotal(invoice);

        invoice = invoiceRepository.save(invoice);
        invoice.getPayments().size();
        return invoice;
    }

    @Transactional
    public Invoice cancelInvoice(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByInvoiceIdForUpdate(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));
        invoice.getPayments().size();

        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new IllegalArgumentException("Invoice cannot be cancelled in status " + invoice.getStatus());
        }
        if (invoice.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("Invoice with recorded payments cannot be cancelled; refund first");
        }

        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setCancelledAt(Instant.now());
        invoice = invoiceRepository.save(invoice);
        invoice.getItems().size();
        eventPublisher.publishInvoiceCancelled(invoice);
        return invoice;
    }

    @Transactional
    public Invoice recordPayment(UUID invoiceId, RecordPaymentRequest request) {
        Invoice invoice = invoiceRepository.findByInvoiceIdForUpdate(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));
        invoice.getPayments().size();

        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot record a payment against a cancelled invoice");
        }

        BigDecimal amount = money(request.getAmount());
        BigDecimal remaining = invoice.getTotalAmount().subtract(invoice.getPaidAmount());
        if (amount.compareTo(remaining) > 0) {
            throw new IllegalArgumentException(
                    "Payment amount exceeds the remaining balance of " + remaining);
        }

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(amount);
        payment.setMethod(request.getMethod());
        payment.setReference(request.getReference());
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(Instant.now());

        invoice.getPayments().add(payment);
        recomputePaymentStatus(invoice);

        invoice = invoiceRepository.save(invoice);
        invoice.getItems().size();
        eventPublisher.publishPaymentConfirmed(invoice, payment);
        return invoice;
    }

    @Transactional
    public Invoice refundPayment(UUID paymentId) {
        Payment payment = paymentRepository.findByPaymentIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalArgumentException("Only completed payments can be refunded");
        }

        Invoice invoice = payment.getInvoice();
        invoice.getPayments().size();
        invoice.getItems().size();
        payment.setStatus(PaymentStatus.REFUNDED);
        recomputePaymentStatus(invoice);

        invoice = invoiceRepository.save(invoice);
        eventPublisher.publishPaymentRefunded(invoice, payment);
        return invoice;
    }

    private void applyItems(Invoice invoice, List<InvoiceItem> items) {
        invoice.getItems().clear();
        invoice.getItems().addAll(items);
        for (InvoiceItem item : items) {
            item.setInvoice(invoice);
        }
    }

    private List<InvoiceItem> buildItems(List<CreateInvoiceItemRequest> requests, Invoice invoice) {
        List<InvoiceItem> items = new ArrayList<>();
        for (CreateInvoiceItemRequest request : requests) {
            BigDecimal quantity = money(request.getQuantity());
            BigDecimal unitPrice = money(request.getUnitPrice());
            BigDecimal amount = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

            InvoiceItem item = new InvoiceItem();
            item.setInvoice(invoice);
            item.setItemType(request.getItemType() == null
                    ? com.mediflow.billing.entity.InvoiceItemType.OTHER
                    : request.getItemType());
            item.setDescription(request.getDescription());
            item.setQuantity(quantity);
            item.setUnitPrice(unitPrice);
            item.setAmount(amount);
            items.add(item);
        }
        return items;
    }

    private void applyTotals(Invoice invoice) {
        BigDecimal subtotal = invoice.getItems().stream()
                .map(InvoiceItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal taxable = subtotal.subtract(invoice.getDiscount());
        BigDecimal taxAmount = taxable.multiply(invoice.getTaxRate())
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal total = taxable.add(taxAmount);

        invoice.setSubtotal(subtotal);
        invoice.setTaxAmount(taxAmount);
        invoice.setTotalAmount(total);
    }

    private void recomputePaymentStatus(Invoice invoice) {
        BigDecimal paid = invoice.getPayments().stream()
                .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        invoice.setPaidAmount(paid.setScale(2, RoundingMode.HALF_UP));

        if (paid.compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
            if (invoice.getPaidAt() == null) {
                invoice.setPaidAt(Instant.now());
            }
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
            invoice.setPaidAt(null);
        } else {
            invoice.setStatus(InvoiceStatus.PENDING);
            invoice.setPaidAt(null);
        }
    }

    private void requirePositiveTotal(Invoice invoice) {
        if (invoice.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invoice total must be greater than zero");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String generateInvoiceNumber() {
        String date = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "INV-" + date + "-" + suffix;
    }
}
