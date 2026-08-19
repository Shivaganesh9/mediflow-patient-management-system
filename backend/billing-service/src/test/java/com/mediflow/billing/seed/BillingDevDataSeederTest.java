package com.mediflow.billing.seed;

import com.mediflow.billing.entity.Invoice;
import com.mediflow.billing.entity.InvoiceItem;
import com.mediflow.billing.entity.Payment;
import com.mediflow.billing.repository.InvoiceRepository;
import com.mediflow.billing.repository.PaymentRepository;
import com.mediflow.common.seed.DevSeedData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingDevDataSeederTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private BillingDevDataSeeder seeder;

    @Test
    void seedCreatesTwoInvoicesWithItemsAndOnePayment() {
        when(invoiceRepository.findByInvoiceId(any())).thenReturn(Optional.empty());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        seeder.seed();

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository, times(2)).save(invoiceCaptor.capture());

        Set<java.util.UUID> ids = invoiceCaptor.getAllValues().stream()
                .map(Invoice::getInvoiceId)
                .collect(Collectors.toSet());
        assertEquals(Set.of(DevSeedData.INVOICE_1_ID, DevSeedData.INVOICE_2_ID), ids);

        Invoice paid = invoiceCaptor.getAllValues().stream()
                .filter(i -> i.getInvoiceId().equals(DevSeedData.INVOICE_1_ID))
                .findFirst()
                .orElseThrow();
        assertEquals(DevSeedData.PATIENT_USER_ID, paid.getPatientId());
        assertTrue(paid.getItems().size() >= 2);
        assertEquals(DevSeedData.PAYMENT_1_ID, paid.getPayments().get(0).getPaymentId());

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertEquals(DevSeedData.PAYMENT_1_ID, paymentCaptor.getValue().getPaymentId());
    }

    @Test
    void seedIsIdempotentWhenInvoicesExist() {
        when(invoiceRepository.findByInvoiceId(any()))
                .thenAnswer(inv -> Optional.of(new Invoice()));

        seeder.seed();
        seeder.seed();

        verify(invoiceRepository, never()).save(any(Invoice.class));
        verify(paymentRepository, never()).save(any(Payment.class));
    }
}