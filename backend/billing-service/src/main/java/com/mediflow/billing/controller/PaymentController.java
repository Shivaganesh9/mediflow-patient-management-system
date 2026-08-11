package com.mediflow.billing.controller;

import com.mediflow.billing.dto.InvoiceResponse;
import com.mediflow.billing.mapper.InvoiceMapper;
import com.mediflow.billing.service.InvoiceService;
import com.mediflow.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final InvoiceService invoiceService;
    private final InvoiceMapper invoiceMapper;

    @PostMapping("/{paymentId}/refund")
    public ApiResponse<InvoiceResponse> refundPayment(@PathVariable UUID paymentId) {
        return ApiResponse.success(
                invoiceMapper.toResponse(invoiceService.refundPayment(paymentId)),
                "Payment refunded successfully");
    }
}
