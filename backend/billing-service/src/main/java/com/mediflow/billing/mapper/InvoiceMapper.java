package com.mediflow.billing.mapper;

import com.mediflow.billing.dto.InvoiceItemResponse;
import com.mediflow.billing.dto.InvoiceResponse;
import com.mediflow.billing.dto.PaymentResponse;
import com.mediflow.billing.entity.Invoice;
import com.mediflow.billing.entity.InvoiceItem;
import com.mediflow.billing.entity.Payment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class InvoiceMapper {

    public InvoiceResponse toResponse(Invoice invoice) {
        BigDecimal remaining = invoice.getTotalAmount().subtract(invoice.getPaidAmount());

        List<InvoiceItemResponse> items = invoice.getItems() == null
                ? List.of()
                : invoice.getItems().stream().map(this::toItemResponse).toList();

        List<PaymentResponse> payments = invoice.getPayments() == null
                ? List.of()
                : invoice.getPayments().stream().map(this::toPaymentResponse).toList();

        return InvoiceResponse.builder()
                .invoiceId(invoice.getInvoiceId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .patientId(invoice.getPatientId())
                .appointmentId(invoice.getAppointmentId())
                .status(invoice.getStatus())
                .currency(invoice.getCurrency())
                .subtotal(invoice.getSubtotal())
                .taxRate(invoice.getTaxRate())
                .taxAmount(invoice.getTaxAmount())
                .discount(invoice.getDiscount())
                .totalAmount(invoice.getTotalAmount())
                .paidAmount(invoice.getPaidAmount())
                .remainingAmount(remaining)
                .dueDate(invoice.getDueDate())
                .issuedAt(invoice.getIssuedAt())
                .paidAt(invoice.getPaidAt())
                .cancelledAt(invoice.getCancelledAt())
                .notes(invoice.getNotes())
                .version(invoice.getVersion())
                .items(items)
                .payments(payments)
                .build();
    }

    private InvoiceItemResponse toItemResponse(InvoiceItem item) {
        return InvoiceItemResponse.builder()
                .itemId(item.getItemId())
                .itemType(item.getItemType())
                .description(item.getDescription())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .amount(item.getAmount())
                .build();
    }

    public PaymentResponse toPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .invoiceId(payment.getInvoice().getInvoiceId())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .reference(payment.getReference())
                .status(payment.getStatus())
                .paidAt(payment.getPaidAt())
                .build();
    }
}
