package com.mediflow.billing.controller;

import com.mediflow.billing.dto.CreateInvoiceRequest;
import com.mediflow.billing.entity.Invoice;
import com.mediflow.billing.service.InvoiceService;
import com.mediflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Invoice> createInvoice(@Valid @RequestBody CreateInvoiceRequest request) {
        return ApiResponse.success(invoiceService.createInvoice(request), "Invoice created successfully");
    }

    @GetMapping
    public ApiResponse<List<Invoice>> getAllInvoices() {
        return ApiResponse.success(invoiceService.getAllInvoices(), "Invoices retrieved successfully");
    }

    @GetMapping("/{invoiceId}")
    public ApiResponse<Invoice> getInvoice(@PathVariable UUID invoiceId) {
        return ApiResponse.success(invoiceService.getInvoice(invoiceId), "Invoice retrieved successfully");
    }

    @PatchMapping("/{invoiceId}/pay")
    public ApiResponse<Invoice> markInvoicePaid(@PathVariable UUID invoiceId) {
        return ApiResponse.success(invoiceService.markInvoicePaid(invoiceId), "Invoice marked as paid");
    }
}
