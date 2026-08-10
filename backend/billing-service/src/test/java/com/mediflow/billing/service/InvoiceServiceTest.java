package com.mediflow.billing.service;

import com.mediflow.billing.dto.CreateInvoiceRequest;
import com.mediflow.billing.entity.Invoice;
import com.mediflow.billing.repository.InvoiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private InvoiceService invoiceService;

    @Test
    void shouldCreateInvoice() {
        CreateInvoiceRequest request = new CreateInvoiceRequest();
        request.setPatientId(java.util.UUID.randomUUID());
        request.setAmount(125.0);
        request.setCurrency("USD");
        request.setDescription("Consultation");

        Invoice savedInvoice = new Invoice();
        savedInvoice.setPatientId(request.getPatientId());
        savedInvoice.setAmount(request.getAmount());
        savedInvoice.setCurrency(request.getCurrency());
        savedInvoice.setDescription(request.getDescription());

        when(invoiceRepository.save(any(Invoice.class))).thenReturn(savedInvoice);

        Invoice result = invoiceService.createInvoice(request);

        assertNotNull(result);
        assertEquals("USD", result.getCurrency());
        assertEquals(125.0, result.getAmount());
        verify(invoiceRepository).save(any(Invoice.class));
    }
}
