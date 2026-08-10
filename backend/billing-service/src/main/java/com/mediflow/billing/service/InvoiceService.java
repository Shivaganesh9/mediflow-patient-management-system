package com.mediflow.billing.service;

import com.mediflow.billing.dto.CreateInvoiceRequest;
import com.mediflow.billing.entity.Invoice;
import com.mediflow.billing.repository.InvoiceRepository;
import com.mediflow.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;

    @Transactional
    public Invoice createInvoice(CreateInvoiceRequest request) {
        Invoice invoice = new Invoice();
        invoice.setPatientId(request.getPatientId());
        invoice.setAmount(request.getAmount());
        invoice.setCurrency(request.getCurrency());
        invoice.setDescription(request.getDescription());
        return invoiceRepository.save(invoice);
    }

    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    public Invoice getInvoice(UUID invoiceId) {
        return invoiceRepository.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));
    }

    @Transactional
    public Invoice markInvoicePaid(UUID invoiceId) {
        Invoice invoice = getInvoice(invoiceId);
        invoice.setStatus("PAID");
        return invoiceRepository.save(invoice);
    }
}
