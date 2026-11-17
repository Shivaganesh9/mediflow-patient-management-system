package com.mediflow.billing.controller;

import com.mediflow.billing.dto.CreateInvoiceRequest;
import com.mediflow.billing.dto.InvoiceResponse;
import com.mediflow.billing.dto.PaymentResponse;
import com.mediflow.billing.dto.RecordPaymentRequest;
import com.mediflow.billing.dto.UpdateInvoiceRequest;
import com.mediflow.billing.entity.InvoiceStatus;
import com.mediflow.billing.mapper.InvoiceMapper;
import com.mediflow.billing.service.InvoiceService;
import com.mediflow.billing.service.PaymentCommandService;
import com.mediflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    /** Standard header carrying the caller's idempotency key for payment creation. */
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    /** Set on a replayed response so clients can tell they received the original result. */
    private static final String IDEMPOTENT_REPLAY_HEADER = "Idempotent-Replay";

    private final InvoiceService invoiceService;
    private final PaymentCommandService paymentCommandService;
    private final InvoiceMapper invoiceMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InvoiceResponse> createInvoice(@Valid @RequestBody CreateInvoiceRequest request) {
        return ApiResponse.success(
                invoiceMapper.toResponse(invoiceService.createInvoice(request)),
                "Invoice generated successfully");
    }

    @GetMapping
    public ApiResponse<Page<InvoiceResponse>> getInvoices(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID appointmentId,
            @PageableDefault(size = 20, sort = "issuedAt") Pageable pageable) {
        Page<InvoiceResponse> invoices = invoiceService
                .searchInvoices(status, patientId, appointmentId, pageable)
                .map(invoiceMapper::toResponse);
        return ApiResponse.success(invoices, "Invoices retrieved successfully");
    }

    @GetMapping("/{invoiceId}")
    public ApiResponse<InvoiceResponse> getInvoice(@PathVariable UUID invoiceId) {
        return ApiResponse.success(
                invoiceMapper.toResponse(invoiceService.getInvoice(invoiceId)),
                "Invoice retrieved successfully");
    }

    @PutMapping("/{invoiceId}")
    public ApiResponse<InvoiceResponse> updateInvoice(@PathVariable UUID invoiceId,
                                                      @Valid @RequestBody UpdateInvoiceRequest request) {
        return ApiResponse.success(
                invoiceMapper.toResponse(invoiceService.updateInvoice(invoiceId, request)),
                "Invoice updated successfully");
    }

    @PostMapping("/{invoiceId}/cancel")
    public ApiResponse<InvoiceResponse> cancelInvoice(@PathVariable UUID invoiceId) {
        return ApiResponse.success(
                invoiceMapper.toResponse(invoiceService.cancelInvoice(invoiceId)),
                "Invoice cancelled successfully");
    }

    /**
     * Records a payment. Send an {@code Idempotency-Key} header to make retries safe: replaying the
     * same key with the same payload returns the original invoice instead of creating a second
     * payment, while reusing it with a different payload is rejected with 409.
     */
    @PostMapping("/{invoiceId}/payments")
    public ResponseEntity<ApiResponse<InvoiceResponse>> recordPayment(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody RecordPaymentRequest request,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {
        PaymentCommandService.PaymentResult result =
                paymentCommandService.recordPayment(invoiceId, request, idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED);
        if (result.replayed()) {
            response.header(IDEMPOTENT_REPLAY_HEADER, "true");
        }
        return response.body(ApiResponse.success(
                invoiceMapper.toResponse(result.invoice()),
                result.replayed() ? "Payment already recorded; original result returned"
                        : "Payment recorded successfully"));
    }

    @GetMapping("/{invoiceId}/payments")
    public ApiResponse<List<PaymentResponse>> getPayments(@PathVariable UUID invoiceId) {
        List<PaymentResponse> payments = invoiceService.getPayments(invoiceId).stream()
                .map(invoiceMapper::toPaymentResponse)
                .toList();
        return ApiResponse.success(payments, "Payments retrieved successfully");
    }
}
