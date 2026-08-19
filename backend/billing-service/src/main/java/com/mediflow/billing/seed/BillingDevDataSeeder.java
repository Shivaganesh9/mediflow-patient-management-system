package com.mediflow.billing.seed;

import com.mediflow.billing.entity.Invoice;
import com.mediflow.billing.entity.InvoiceItem;
import com.mediflow.billing.entity.InvoiceItemType;
import com.mediflow.billing.entity.InvoiceStatus;
import com.mediflow.billing.entity.Payment;
import com.mediflow.billing.entity.PaymentMethod;
import com.mediflow.billing.entity.PaymentStatus;
import com.mediflow.billing.repository.InvoiceRepository;
import com.mediflow.billing.repository.PaymentRepository;
import com.mediflow.common.seed.DevSeedData;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Dev-only seeder (active under the {@code seed} profile) that creates two deterministic
 * invoices (one paid, one pending) for the seeded patient IDs, plus a completed payment for
 * the paid invoice. Idempotent: existing invoice IDs and payment IDs are skipped.
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
public class BillingDevDataSeeder implements ApplicationRunner {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    @Transactional
    public void seed() {
        createPaidInvoice();
        createPendingInvoice();
    }

    private void createPaidInvoice() {
        if (invoiceRepository.findByInvoiceId(DevSeedData.INVOICE_1_ID).isPresent()) {
            return;
        }
        Invoice invoice = new Invoice();
        invoice.setInvoiceId(DevSeedData.INVOICE_1_ID);
        invoice.setInvoiceNumber("INV-SEED-0001");
        invoice.setPatientId(DevSeedData.PATIENT_USER_ID);
        invoice.setAppointmentId(DevSeedData.APPOINTMENT_1_ID);
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setTaxRate(new BigDecimal("5.00"));
        invoice.setCurrency("USD");
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setIssuedAt(Instant.now());
        invoice.setPaidAt(Instant.now());
        invoice.setNotes("Seeded paid invoice");

        InvoiceItem consultation = buildItem(invoice, InvoiceItemType.CONSULTATION,
                "Cardiology consultation", "1", "150.00");
        InvoiceItem medication = buildItem(invoice, InvoiceItemType.MEDICATION,
                "Prescribed medication", "2", "25.00");
        invoice.getItems().add(consultation);
        invoice.getItems().add(medication);
        applyTotals(invoice);

        Payment payment = new Payment();
        payment.setPaymentId(DevSeedData.PAYMENT_1_ID);
        payment.setInvoice(invoice);
        payment.setAmount(invoice.getTotalAmount());
        payment.setMethod(PaymentMethod.CARD);
        payment.setReference("SEED-PAY-0001");
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(Instant.now());
        invoice.getPayments().add(payment);

        invoiceRepository.save(invoice);
        paymentRepository.save(payment);
    }

    private void createPendingInvoice() {
        if (invoiceRepository.findByInvoiceId(DevSeedData.INVOICE_2_ID).isPresent()) {
            return;
        }
        Invoice invoice = new Invoice();
        invoice.setInvoiceId(DevSeedData.INVOICE_2_ID);
        invoice.setInvoiceNumber("INV-SEED-0002");
        invoice.setPatientId(DevSeedData.SECOND_PATIENT_ID);
        invoice.setAppointmentId(DevSeedData.APPOINTMENT_3_ID);
        invoice.setStatus(InvoiceStatus.PENDING);
        invoice.setTaxRate(new BigDecimal("5.00"));
        invoice.setCurrency("USD");
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setIssuedAt(Instant.now());
        invoice.setNotes("Seeded pending invoice");

        InvoiceItem consultation = buildItem(invoice, InvoiceItemType.CONSULTATION,
                "Dermatology consultation", "1", "120.00");
        invoice.getItems().add(consultation);
        applyTotals(invoice);

        invoiceRepository.save(invoice);
    }

    private InvoiceItem buildItem(Invoice invoice, InvoiceItemType type, String description,
                                  String quantity, String unitPrice) {
        BigDecimal qty = new BigDecimal(quantity);
        BigDecimal unit = new BigDecimal(unitPrice);
        InvoiceItem item = new InvoiceItem();
        item.setInvoice(invoice);
        item.setItemType(type);
        item.setDescription(description);
        item.setQuantity(qty);
        item.setUnitPrice(unit);
        item.setAmount(qty.multiply(unit).setScale(2, RoundingMode.HALF_UP));
        return item;
    }

    private void applyTotals(Invoice invoice) {
        BigDecimal subtotal = invoice.getItems().stream()
                .map(InvoiceItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal taxable = subtotal.subtract(invoice.getDiscount());
        BigDecimal taxAmount = taxable.multiply(invoice.getTaxRate())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = taxable.add(taxAmount);
        invoice.setSubtotal(subtotal);
        invoice.setTaxAmount(taxAmount);
        invoice.setTotalAmount(total);
        invoice.setPaidAmount(BigDecimal.ZERO.setScale(2));
    }
}