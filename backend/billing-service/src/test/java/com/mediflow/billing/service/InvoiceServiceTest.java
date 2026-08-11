package com.mediflow.billing.service;

import com.mediflow.billing.dto.CreateInvoiceItemRequest;
import com.mediflow.billing.dto.CreateInvoiceRequest;
import com.mediflow.billing.dto.RecordPaymentRequest;
import com.mediflow.billing.dto.UpdateInvoiceRequest;
import com.mediflow.billing.entity.Invoice;
import com.mediflow.billing.entity.InvoiceItemType;
import com.mediflow.billing.entity.InvoiceStatus;
import com.mediflow.billing.entity.Payment;
import com.mediflow.billing.entity.PaymentMethod;
import com.mediflow.billing.entity.PaymentStatus;
import com.mediflow.billing.event.BillingEventPublisher;
import com.mediflow.billing.repository.InvoiceRepository;
import com.mediflow.billing.repository.PaymentRepository;
import com.mediflow.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BillingEventPublisher eventPublisher;

    @InjectMocks
    private InvoiceService invoiceService;

    @Test
    void createInvoiceComputesTotalsAndPublishes() {
        CreateInvoiceRequest request = createInvoiceRequest(2, "100.00", 1, "50.00", "10.00", "25.00");

        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice result = invoiceService.createInvoice(request);

        assertNotNull(result.getInvoiceNumber());
        assertTrue(result.getInvoiceNumber().startsWith("INV-"));
        assertEquals(InvoiceStatus.PENDING, result.getStatus());
        assertEquals(new BigDecimal("250.00"), result.getSubtotal());
        assertEquals(new BigDecimal("22.50"), result.getTaxAmount());
        assertEquals(new BigDecimal("247.50"), result.getTotalAmount());
        assertEquals(0, result.getPaidAmount().compareTo(BigDecimal.ZERO));
        assertEquals(2, result.getItems().size());
        verify(invoiceRepository).save(any(Invoice.class));
        verify(eventPublisher).publishInvoiceGenerated(any(Invoice.class));
    }

    @Test
    void createInvoiceDefaultsMissingFinancialFieldsToZero() {
        CreateInvoiceRequest request = createInvoiceRequest(1, "100.00", null, null, null, null);

        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice result = invoiceService.createInvoice(request);

        assertEquals(new BigDecimal("100.00"), result.getSubtotal());
        assertEquals(new BigDecimal("0.00"), result.getTaxAmount());
        assertEquals(new BigDecimal("100.00"), result.getTotalAmount());
    }

    @Test
    void createInvoiceRejectsEmptyItems() {
        CreateInvoiceRequest request = new CreateInvoiceRequest();
        request.setPatientId(UUID.randomUUID());
        request.setCurrency("USD");
        request.setItems(List.of());

        assertThrows(IllegalArgumentException.class, () -> invoiceService.createInvoice(request));
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void createInvoiceRejectsDiscountExceedingSubtotal() {
        CreateInvoiceRequest request = createInvoiceRequest(1, "100.00", null, null, "0.00", "200.00");

        assertThrows(IllegalArgumentException.class, () -> invoiceService.createInvoice(request));
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void getInvoiceReturnsWhenFound() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = invoiceWithId(invoiceId);
        when(invoiceRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.of(invoice));

        assertEquals(invoice, invoiceService.getInvoice(invoiceId));
    }

    @Test
    void getInvoiceThrowsWhenMissing() {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> invoiceService.getInvoice(invoiceId));
    }

    @Test
    void getPaymentsReturnsInvoicePayments() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = invoiceWithId(invoiceId);
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("50.00"));
        invoice.getPayments().add(payment);
        when(invoiceRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.of(invoice));

        assertEquals(1, invoiceService.getPayments(invoiceId).size());
    }

    @Test
    void updateInvoiceReplacesItemsAndRecomputesTotals() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = pendingInvoice(invoiceId);
        when(invoiceRepository.findByInvoiceIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateInvoiceRequest request = new UpdateInvoiceRequest();
        request.setTaxRate(new BigDecimal("5.00"));
        request.setItems(List.of(itemRequest(1, "200.00")));

        Invoice result = invoiceService.updateInvoice(invoiceId, request);

        assertEquals(new BigDecimal("200.00"), result.getSubtotal());
        assertEquals(new BigDecimal("10.00"), result.getTaxAmount());
        assertEquals(new BigDecimal("210.00"), result.getTotalAmount());
        assertEquals(1, result.getItems().size());
    }

    @Test
    void updateInvoiceRejectsNonPendingStatus() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = invoiceWithId(invoiceId);
        invoice.setStatus(InvoiceStatus.PAID);
        when(invoiceRepository.findByInvoiceIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));

        UpdateInvoiceRequest request = new UpdateInvoiceRequest();
        request.setItems(List.of(itemRequest(1, "200.00")));

        assertThrows(IllegalArgumentException.class, () -> invoiceService.updateInvoice(invoiceId, request));
    }

    @Test
    void updateInvoiceThrowsWhenMissing() {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceRepository.findByInvoiceIdForUpdate(invoiceId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> invoiceService.updateInvoice(invoiceId, new UpdateInvoiceRequest()));
    }

    @Test
    void cancelInvoiceCancelsAndPublishes() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = pendingInvoice(invoiceId);
        when(invoiceRepository.findByInvoiceIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice result = invoiceService.cancelInvoice(invoiceId);

        assertEquals(InvoiceStatus.CANCELLED, result.getStatus());
        assertNotNull(result.getCancelledAt());
        verify(eventPublisher).publishInvoiceCancelled(any(Invoice.class));
    }

    @Test
    void cancelInvoiceRejectsWhenPaymentsRecorded() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = pendingInvoice(invoiceId);
        invoice.getPayments().add(completedPayment(invoice, new BigDecimal("50.00")));
        invoice.setPaidAmount(new BigDecimal("50.00"));
        when(invoiceRepository.findByInvoiceIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));

        assertThrows(IllegalArgumentException.class, () -> invoiceService.cancelInvoice(invoiceId));
    }

    @Test
    void cancelInvoiceRejectsWhenAlreadyPaid() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = invoiceWithId(invoiceId);
        invoice.setStatus(InvoiceStatus.PAID);
        when(invoiceRepository.findByInvoiceIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));

        assertThrows(IllegalArgumentException.class, () -> invoiceService.cancelInvoice(invoiceId));
    }

    @Test
    void cancelInvoiceThrowsWhenMissing() {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceRepository.findByInvoiceIdForUpdate(invoiceId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> invoiceService.cancelInvoice(invoiceId));
    }

    @Test
    void recordPaymentMarksInvoicePaidWhenFullAmount() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = pendingInvoice(invoiceId);
        invoice.setTotalAmount(new BigDecimal("100.00"));
        when(invoiceRepository.findByInvoiceIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice result = invoiceService.recordPayment(invoiceId, paymentRequest("100.00"));

        assertEquals(InvoiceStatus.PAID, result.getStatus());
        assertEquals(new BigDecimal("100.00"), result.getPaidAmount());
        assertNotNull(result.getPaidAt());
        verify(eventPublisher).publishPaymentConfirmed(any(Invoice.class), any(Payment.class));
    }

    @Test
    void recordPaymentMarksInvoicePartiallyPaid() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = pendingInvoice(invoiceId);
        invoice.setTotalAmount(new BigDecimal("100.00"));
        when(invoiceRepository.findByInvoiceIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice result = invoiceService.recordPayment(invoiceId, paymentRequest("40.00"));

        assertEquals(InvoiceStatus.PARTIALLY_PAID, result.getStatus());
        assertEquals(new BigDecimal("40.00"), result.getPaidAmount());
    }

    @Test
    void recordPaymentRejectsAmountExceedingRemaining() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = pendingInvoice(invoiceId);
        invoice.setTotalAmount(new BigDecimal("100.00"));
        when(invoiceRepository.findByInvoiceIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));

        assertThrows(IllegalArgumentException.class,
                () -> invoiceService.recordPayment(invoiceId, paymentRequest("150.00")));
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void recordPaymentRejectsCancelledInvoice() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = invoiceWithId(invoiceId);
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setTotalAmount(new BigDecimal("100.00"));
        when(invoiceRepository.findByInvoiceIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));

        assertThrows(IllegalArgumentException.class,
                () -> invoiceService.recordPayment(invoiceId, paymentRequest("50.00")));
    }

    @Test
    void recordPaymentThrowsWhenInvoiceMissing() {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceRepository.findByInvoiceIdForUpdate(invoiceId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> invoiceService.recordPayment(invoiceId, paymentRequest("50.00")));
    }

    @Test
    void refundPaymentRefundsCompletedPayment() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = pendingInvoice(invoiceId);
        invoice.setTotalAmount(new BigDecimal("100.00"));
        Payment payment = completedPayment(invoice, new BigDecimal("100.00"));
        invoice.getPayments().add(payment);
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAmount(new BigDecimal("100.00"));

        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findByPaymentIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice result = invoiceService.refundPayment(paymentId);

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(InvoiceStatus.PENDING, result.getStatus());
        assertEquals(0, result.getPaidAmount().compareTo(BigDecimal.ZERO));
        verify(eventPublisher).publishPaymentRefunded(any(Invoice.class), any(Payment.class));
    }

    @Test
    void refundPaymentRejectsAlreadyRefunded() {
        Invoice invoice = invoiceWithId(UUID.randomUUID());
        Payment payment = completedPayment(invoice, new BigDecimal("50.00"));
        payment.setStatus(PaymentStatus.REFUNDED);

        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findByPaymentIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        assertThrows(IllegalArgumentException.class, () -> invoiceService.refundPayment(paymentId));
    }

    @Test
    void refundPaymentThrowsWhenMissing() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findByPaymentIdForUpdate(paymentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> invoiceService.refundPayment(paymentId));
    }

    @Test
    void searchInvoicesReturnsPage() {
        when(invoiceRepository.search(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(invoiceWithId(UUID.randomUUID()))));

        var result = invoiceService.searchInvoices(InvoiceStatus.PENDING, UUID.randomUUID(), null, Pageable.unpaged());

        assertEquals(1, result.getTotalElements());
    }

    private CreateInvoiceRequest createInvoiceRequest(int qty1, String price1, Integer qty2, String price2,
                                                      String taxRate, String discount) {
        CreateInvoiceRequest request = new CreateInvoiceRequest();
        request.setPatientId(UUID.randomUUID());
        request.setCurrency("USD");
        if (taxRate != null) {
            request.setTaxRate(new BigDecimal(taxRate));
        }
        if (discount != null) {
            request.setDiscount(new BigDecimal(discount));
        }
        CreateInvoiceItemRequest item1 = itemRequest(qty1, price1);
        if (qty2 != null && qty2 > 0 && price2 != null) {
            CreateInvoiceItemRequest item2 = itemRequest(qty2, price2);
            request.setItems(List.of(item1, item2));
        } else {
            request.setItems(List.of(item1));
        }
        return request;
    }

    private CreateInvoiceItemRequest itemRequest(int quantity, String unitPrice) {
        CreateInvoiceItemRequest item = new CreateInvoiceItemRequest();
        item.setItemType(InvoiceItemType.CONSULTATION);
        item.setDescription("Consultation");
        item.setQuantity(new BigDecimal(quantity));
        item.setUnitPrice(new BigDecimal(unitPrice));
        return item;
    }

    private RecordPaymentRequest paymentRequest(String amount) {
        RecordPaymentRequest request = new RecordPaymentRequest();
        request.setAmount(new BigDecimal(amount));
        request.setMethod(PaymentMethod.CARD);
        return request;
    }

    private Invoice invoiceWithId(UUID invoiceId) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceId(invoiceId);
        invoice.setInvoiceNumber("INV-TEST");
        invoice.setIssuedAt(Instant.now());
        return invoice;
    }

    private Invoice pendingInvoice(UUID invoiceId) {
        Invoice invoice = invoiceWithId(invoiceId);
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoice;
    }

    private Payment completedPayment(Invoice invoice, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(amount);
        payment.setMethod(PaymentMethod.CASH);
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(Instant.now());
        return payment;
    }
}
