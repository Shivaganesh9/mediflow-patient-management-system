package com.mediflow.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class UpdateInvoiceRequest {

    @DecimalMin(value = "0.00")
    private BigDecimal taxRate;

    @DecimalMin(value = "0.00")
    private BigDecimal discount;

    private LocalDate dueDate;

    @Size(max = 1000)
    private String notes;

    @Valid
    private List<CreateInvoiceItemRequest> items;
}
